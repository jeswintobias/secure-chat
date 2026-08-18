import { TestBed } from '@angular/core/testing';
import { KeyManagementService } from './key-management.service';
import { CryptoService } from './crypto.service';
import { KeyApiService } from './key-api.service';
import { AuthService } from './auth.service';
import { of } from 'rxjs';

describe('KeyManagementService', () => {
  let service: KeyManagementService;
  let cryptoSpy: jasmine.SpyObj<CryptoService>;
  let apiSpy: jasmine.SpyObj<KeyApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    cryptoSpy = jasmine.createSpyObj('CryptoService', [
      'hasKeyPair',
      'generateKeyPair',
      'storeKeyPair',
      'exportPublicKeyAsString',
      'getPrivateKey',
      'getPublicKey',
      'importPublicKeyFromString',
      'deriveSharedKey',
      'storeConversationKey',
      'getConversationKey',
      'generateGroupKey',
      'wrapGroupKey',
      'unwrapGroupKey'
    ]);
    apiSpy = jasmine.createSpyObj('KeyApiService', [
      'uploadPublicKey',
      'getConversationMemberKeys',
      'getMyKeyBundle',
      'uploadKeyBundles'
    ]);
    authSpy = jasmine.createSpyObj('AuthService', ['getCurrentUsername']);

    TestBed.configureTestingModule({
      providers: [
        { provide: CryptoService, useValue: cryptoSpy },
        { provide: KeyApiService, useValue: apiSpy },
        { provide: AuthService, useValue: authSpy }
      ]
    });
    service = TestBed.inject(KeyManagementService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should initialize keys if none exist', async () => {
    authSpy.getCurrentUsername.and.returnValue('testuser');
    cryptoSpy.hasKeyPair.and.resolveTo(false);
    
    const mockKeyPair = {} as CryptoKeyPair;
    cryptoSpy.generateKeyPair.and.resolveTo(mockKeyPair);
    cryptoSpy.exportPublicKeyAsString.and.resolveTo('pubkey123');
    apiSpy.uploadPublicKey.and.returnValue(of({ userId: '1', keyAlgorithm: 'alg', createdAt: 'now' }));

    await service.initializeKeys();

    expect(cryptoSpy.generateKeyPair).toHaveBeenCalled();
    expect(cryptoSpy.storeKeyPair).toHaveBeenCalledWith('testuser', mockKeyPair);
    expect(apiSpy.uploadPublicKey).toHaveBeenCalledWith('pubkey123');
  });

  it('should skip key generation if keys already exist', async () => {
    authSpy.getCurrentUsername.and.returnValue('testuser');
    cryptoSpy.hasKeyPair.and.resolveTo(true);

    await service.initializeKeys();

    expect(cryptoSpy.generateKeyPair).not.toHaveBeenCalled();
    expect(apiSpy.uploadPublicKey).not.toHaveBeenCalled();
  });
});
