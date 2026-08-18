import { TestBed } from '@angular/core/testing';
import { CryptoService } from './crypto.service';

describe('CryptoService', () => {
  let service: CryptoService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CryptoService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should generate an ECDH key pair', async () => {
    const keyPair = await service.generateKeyPair();
    expect(keyPair).toBeDefined();
    expect(keyPair.privateKey.type).toBe('private');
    expect(keyPair.publicKey.type).toBe('public');
    expect(keyPair.privateKey.extractable).toBeFalse();
    expect(keyPair.publicKey.extractable).toBeTrue();
  });

  it('should encrypt and decrypt a message', async () => {
    const userA = await service.generateKeyPair();
    const userB = await service.generateKeyPair();

    // User A derives shared key
    const sharedKeyA = await service.deriveSharedKey(userA.privateKey, userB.publicKey);
    // User B derives shared key
    const sharedKeyB = await service.deriveSharedKey(userB.privateKey, userA.publicKey);

    const plaintext = 'Secret message with emojis! 🕵️‍♂️🔒';
    const conversationId = 'test-conv-123';

    // Encrypt with A's key
    const { ciphertext, iv } = await service.encryptMessage(plaintext, sharedKeyA, conversationId);
    
    expect(ciphertext).toBeDefined();
    expect(iv).toBeDefined();
    expect(ciphertext).not.toEqual(plaintext);

    // Decrypt with B's key
    const decrypted = await service.decryptMessage(ciphertext, iv, sharedKeyB, conversationId);
    
    expect(decrypted).toEqual(plaintext);
  });

  it('should fail decryption if AAD (conversationId) does not match', async () => {
    const userA = await service.generateKeyPair();
    const userB = await service.generateKeyPair();
    const sharedKey = await service.deriveSharedKey(userA.privateKey, userB.publicKey);

    const plaintext = 'Hello';
    const { ciphertext, iv } = await service.encryptMessage(plaintext, sharedKey, 'conv-A');

    await expectAsync(
      service.decryptMessage(ciphertext, iv, sharedKey, 'conv-B') // Wrong conversation
    ).toBeRejected();
  });

  it('should generate, wrap, and unwrap a group key', async () => {
    const groupKey = await service.generateGroupKey();
    expect(groupKey.extractable).toBeTrue(); // Must be extractable to wrap

    // Wrap the key using a dummy wrapping key
    const dummyKey = await window.crypto.subtle.generateKey(
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );

    const wrapped = await service.wrapGroupKey(groupKey, dummyKey);
    expect(wrapped).toBeDefined();
    expect(typeof wrapped).toBe('string');

    // Unwrap
    const unwrapped = await service.unwrapGroupKey(wrapped, dummyKey);
    expect(unwrapped).toBeDefined();
    expect(unwrapped.extractable).toBeFalse(); // Unwrapped keys should not be extractable
  });
});
