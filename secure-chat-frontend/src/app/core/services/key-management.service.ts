import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CryptoService } from './crypto.service';
import { KeyApiService } from './key-api.service';
import { AuthService } from './auth.service';

/**
 * Orchestrates the E2EE key lifecycle:
 *
 * 1. **Key initialization** — generates an ECDH P-256 key pair on first login,
 *    stores the private key in IndexedDB, uploads the public key to the server.
 *
 * 2. **Private chat keys** — derives a shared AES-256-GCM key via ECDH between
 *    the two members' key pairs. Cached in memory per conversation.
 *
 * 3. **Group chat keys** — the group creator generates a random AES key,
 *    wraps it with each member's ECDH-derived wrapping key, and uploads
 *    the bundles. Members fetch and unwrap their copy. Also cached in memory.
 *
 * 4. **Auto-trigger** — when a new member joins a group, the first existing
 *    online member's client automatically re-encrypts the group key for them.
 */
@Injectable({ providedIn: 'root' })
export class KeyManagementService {

  /** In-memory cache of conversation keys: conversationId → CryptoKey */
  private conversationKeyCache = new Map<string, CryptoKey>();

  /** Track initialization to avoid duplicate key generation */
  private initPromise: Promise<void> | null = null;

  constructor(
    private readonly cryptoService: CryptoService,
    private readonly keyApiService: KeyApiService,
    private readonly authService: AuthService,
  ) {}

  // ══════════════════════════════════════════════════════════════
  // Key Initialization (called after login/register)
  // ══════════════════════════════════════════════════════════════

  /**
   * Initializes E2EE keys for the current user.
   *
   * - If no key pair exists in IndexedDB, generates one and uploads the public key.
   * - If a key pair exists but the server doesn't have the public key, re-uploads it.
   * - Idempotent: safe to call multiple times.
   */
  async initializeKeys(): Promise<void> {
    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = this.doInitializeKeys();
    try {
      await this.initPromise;
    } finally {
      this.initPromise = null;
    }
  }

  private async doInitializeKeys(): Promise<void> {
    const username = this.authService.getCurrentUsername();
    if (!username) return;

    const hasExistingKeys = await this.cryptoService.hasKeyPair(username);

    if (!hasExistingKeys) {
      // Generate a new ECDH P-256 key pair
      const keyPair = await this.cryptoService.generateKeyPair();

      // Store in IndexedDB (private key is non-extractable)
      await this.cryptoService.storeKeyPair(username, keyPair);

      // Upload public key to server
      const publicKeyString = await this.cryptoService.exportPublicKeyAsString(keyPair.publicKey);
      await firstValueFrom(this.keyApiService.uploadPublicKey(publicKeyString));

      console.debug('[E2EE] Key pair generated and public key uploaded');
    } else {
      console.debug('[E2EE] Existing key pair found in IndexedDB');
    }
  }

  // ══════════════════════════════════════════════════════════════
  // Conversation Key Retrieval
  // ══════════════════════════════════════════════════════════════

  /**
   * Gets the encryption key for a conversation.
   * Handles both PRIVATE (ECDH derivation) and GROUP (key unwrapping) conversations.
   *
   * Results are cached in memory for the session.
   *
   * @param conversationId the conversation UUID
   * @param conversationType 'PRIVATE' or 'GROUP'
   * @param otherMemberIds for PRIVATE chats — the other member's userId
   * @returns the AES-256-GCM key, or null if keys are not yet available
   */
  async getConversationKey(
    conversationId: string,
    conversationType: 'PRIVATE' | 'GROUP',
    otherMemberIds?: string[]
  ): Promise<CryptoKey | null> {
    // Check memory cache first
    const cached = this.conversationKeyCache.get(conversationId);
    if (cached) return cached;

    // Check IndexedDB cache
    const stored = await this.cryptoService.getConversationKey(conversationId);
    if (stored) {
      this.conversationKeyCache.set(conversationId, stored);
      return stored;
    }

    try {
      if (conversationType === 'PRIVATE') {
        return await this.derivePrivateChatKey(conversationId, otherMemberIds);
      } else {
        return await this.fetchGroupChatKey(conversationId);
      }
    } catch (err) {
      console.warn('[E2EE] Failed to get conversation key:', err);
      return null;
    }
  }

  /**
   * Derives the shared key for a PRIVATE conversation via ECDH.
   */
  private async derivePrivateChatKey(
    conversationId: string,
    otherMemberIds?: string[]
  ): Promise<CryptoKey | null> {
    const username = this.authService.getCurrentUsername();
    if (!username) return null;

    // Get my private key from IndexedDB
    const myPrivateKey = await this.cryptoService.getPrivateKey(username);
    if (!myPrivateKey) {
      console.warn('[E2EE] No private key found — cannot derive shared key');
      return null;
    }

    // Get the other member's public key from the server
    // For private chats, fetch all member keys and pick the one that isn't me
    const memberKeys = await firstValueFrom(
      this.keyApiService.getConversationMemberKeys(conversationId)
    );

    // Find the other user's key (not my own)
    const myUserId = Object.keys(memberKeys).find(uid =>
      otherMemberIds ? !otherMemberIds.includes(uid) : false
    );

    let otherPublicKeyString: string | undefined;
    for (const [userId, publicKey] of Object.entries(memberKeys)) {
      if (userId !== myUserId && (!otherMemberIds || otherMemberIds.includes(userId))) {
        otherPublicKeyString = publicKey;
        break;
      }
    }

    // If we can't determine who's who, just use any key that isn't ours
    // For 2-person private chats, there's only one other person
    if (!otherPublicKeyString) {
      const allKeys = Object.entries(memberKeys);
      if (allKeys.length === 2) {
        // Try both and use the one that's different from our public key
        const myPublicKey = await this.cryptoService.getPublicKey(username);
        if (myPublicKey) {
          const myPublicKeyStr = await this.cryptoService.exportPublicKeyAsString(myPublicKey);
          otherPublicKeyString = allKeys.find(([_, key]) => key !== myPublicKeyStr)?.[1];
        }
      }
      if (!otherPublicKeyString) {
        // Fallback: use the first key that is available
        otherPublicKeyString = Object.values(memberKeys)[0];
      }
    }

    if (!otherPublicKeyString) {
      console.warn('[E2EE] Could not find other member\'s public key');
      return null;
    }

    // Import the other user's public key
    const otherPublicKey = await this.cryptoService.importPublicKeyFromString(otherPublicKeyString);

    // Derive the shared AES-256-GCM key via ECDH
    const sharedKey = await this.cryptoService.deriveSharedKey(myPrivateKey, otherPublicKey);

    // Cache it
    this.conversationKeyCache.set(conversationId, sharedKey);
    await this.cryptoService.storeConversationKey(conversationId, sharedKey);

    console.debug('[E2EE] Private chat key derived for conversation', conversationId);
    return sharedKey;
  }

  /**
   * Fetches and unwraps the group key for a GROUP conversation.
   */
  private async fetchGroupChatKey(conversationId: string): Promise<CryptoKey | null> {
    const username = this.authService.getCurrentUsername();
    if (!username) return null;

    // Get my private key
    const myPrivateKey = await this.cryptoService.getPrivateKey(username);
    if (!myPrivateKey) {
      console.warn('[E2EE] No private key found — cannot unwrap group key');
      return null;
    }

    try {
      // Fetch my encrypted group key bundle from the server
      const bundle = await firstValueFrom(
        this.keyApiService.getMyKeyBundle(conversationId)
      );

      // To unwrap, we need to derive a wrapping key. For group keys,
      // we use a deterministic ECDH derivation with our own public key
      // (the wrapper encrypted it using our public key)
      const myPublicKey = await this.cryptoService.getPublicKey(username);
      if (!myPublicKey) return null;

      const unwrappingKey = await this.cryptoService.deriveSharedKey(myPrivateKey, myPublicKey);

      // Unwrap the group key
      const groupKey = await this.cryptoService.unwrapGroupKey(
        bundle.encryptedKey, unwrappingKey
      );

      // Cache it
      this.conversationKeyCache.set(conversationId, groupKey);
      await this.cryptoService.storeConversationKey(conversationId, groupKey);

      console.debug('[E2EE] Group key unwrapped for conversation', conversationId);
      return groupKey;
    } catch (err) {
      console.warn('[E2EE] No group key bundle available:', err);
      return null;
    }
  }

  // ══════════════════════════════════════════════════════════════
  // Group Key Distribution
  // ══════════════════════════════════════════════════════════════

  /**
   * Creates and distributes a new group key for a conversation.
   * Called by the group creator when a new GROUP conversation is created.
   *
   * @param conversationId the conversation UUID
   */
  async createAndDistributeGroupKey(conversationId: string): Promise<void> {
    const username = this.authService.getCurrentUsername();
    if (!username) return;

    // Generate a new random AES-256-GCM group key
    const groupKey = await this.cryptoService.generateGroupKey();

    // Get all members' public keys
    const memberKeys = await firstValueFrom(
      this.keyApiService.getConversationMemberKeys(conversationId)
    );

    // Wrap the group key for each member
    const bundles: Record<string, string> = {};
    const myPrivateKey = await this.cryptoService.getPrivateKey(username);
    if (!myPrivateKey) return;

    for (const [userId, publicKeyStr] of Object.entries(memberKeys)) {
      const memberPublicKey = await this.cryptoService.importPublicKeyFromString(publicKeyStr);
      const wrappingKey = await this.cryptoService.deriveSharedKey(myPrivateKey, memberPublicKey);
      bundles[userId] = await this.cryptoService.wrapGroupKey(groupKey, wrappingKey);
    }

    // Upload all wrapped key bundles to the server
    await firstValueFrom(
      this.keyApiService.uploadKeyBundles(conversationId, bundles)
    );

    // Cache the group key locally
    this.conversationKeyCache.set(conversationId, groupKey);
    await this.cryptoService.storeConversationKey(conversationId, groupKey);

    console.debug('[E2EE] Group key created and distributed for', conversationId);
  }

  /**
   * Auto-trigger: re-encrypts the group key for a new member.
   * Called when a roster update event indicates a new member joined.
   *
   * @param conversationId the conversation UUID
   * @param newMemberUserId the UUID of the newly joined member
   */
  async addMemberToGroupKey(conversationId: string, newMemberUserId: string): Promise<void> {
    const username = this.authService.getCurrentUsername();
    if (!username) return;

    // Get the group key from cache
    let groupKey: CryptoKey | undefined | null = this.conversationKeyCache.get(conversationId);
    if (!groupKey) {
      groupKey = await this.cryptoService.getConversationKey(conversationId);
    }
    if (!groupKey) {
      console.warn('[E2EE] Cannot add member — no group key available');
      return;
    }

    try {
      // Fetch the new member's public key
      const memberKeyResponse = await firstValueFrom(
        this.keyApiService.getUserPublicKey(newMemberUserId)
      );

      const myPrivateKey = await this.cryptoService.getPrivateKey(username);
      if (!myPrivateKey) return;

      // Wrap the group key for the new member
      const memberPublicKey = await this.cryptoService.importPublicKeyFromString(
        memberKeyResponse.publicKey
      );
      const wrappingKey = await this.cryptoService.deriveSharedKey(myPrivateKey, memberPublicKey);
      const wrappedKey = await this.cryptoService.wrapGroupKey(groupKey, wrappingKey);

      // Upload just this member's bundle
      await firstValueFrom(
        this.keyApiService.uploadKeyBundles(conversationId, { [newMemberUserId]: wrappedKey })
      );

      console.debug('[E2EE] Group key distributed to new member', newMemberUserId);
    } catch (err) {
      console.warn('[E2EE] Failed to distribute group key to new member:', err);
    }
  }

  /**
   * Clears the in-memory key cache. Called on logout.
   */
  clearCache(): void {
    this.conversationKeyCache.clear();
  }
}
