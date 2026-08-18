import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * REST service for E2EE key bundle management.
 *
 * Backend endpoints (KeyBundleController):
 *   PUT  /api/keys/me                           — Upload my public key
 *   GET  /api/keys/user/{userId}                — Get a user's public key
 *   GET  /api/keys/conversation/{id}            — Get all members' public keys
 *   GET  /api/keys/conversation/{id}/bundle     — Get my encrypted group key
 *   PUT  /api/keys/conversation/{id}/bundle     — Upload group key bundles
 */
@Injectable({ providedIn: 'root' })
export class KeyApiService {

  private readonly baseUrl = `${environment.apiUrl}/keys`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Uploads the current user's ECDH public key to the server.
   *
   * @param publicKey Base64-encoded JWK public key
   */
  uploadPublicKey(publicKey: string): Observable<{ userId: string; keyAlgorithm: string; createdAt: string }> {
    return this.http.put<{ userId: string; keyAlgorithm: string; createdAt: string }>(
      `${this.baseUrl}/me`,
      { publicKey }
    );
  }

  /**
   * Retrieves a specific user's ECDH public key.
   *
   * @param userId the target user's UUID
   */
  getUserPublicKey(userId: string): Observable<{ userId: string; publicKey: string; keyAlgorithm: string }> {
    return this.http.get<{ userId: string; publicKey: string; keyAlgorithm: string }>(
      `${this.baseUrl}/user/${userId}`
    );
  }

  /**
   * Retrieves public keys for all members of a conversation.
   * Returns a map of userId → Base64-encoded JWK public key.
   *
   * @param conversationId the conversation UUID
   */
  getConversationMemberKeys(conversationId: string): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(
      `${this.baseUrl}/conversation/${conversationId}`
    );
  }

  /**
   * Retrieves the current user's encrypted group key for a conversation.
   *
   * @param conversationId the conversation UUID
   */
  getMyKeyBundle(conversationId: string): Observable<{ conversationId: string; encryptedKey: string; keyVersion: number }> {
    return this.http.get<{ conversationId: string; encryptedKey: string; keyVersion: number }>(
      `${this.baseUrl}/conversation/${conversationId}/bundle`
    );
  }

  /**
   * Uploads encrypted group key bundles for members of a conversation.
   *
   * @param conversationId the conversation UUID
   * @param bundles map of userId → encrypted key (Base64)
   */
  uploadKeyBundles(conversationId: string, bundles: Record<string, string>): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/conversation/${conversationId}/bundle`,
      bundles
    );
  }
}
