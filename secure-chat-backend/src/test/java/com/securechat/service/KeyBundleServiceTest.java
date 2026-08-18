package com.securechat.service;

import com.securechat.entity.Conversation;
import com.securechat.entity.ConversationKeyBundle;
import com.securechat.entity.User;
import com.securechat.entity.UserKeyBundle;
import com.securechat.repository.ConversationKeyBundleRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.UserKeyBundleRepository;
import com.securechat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeyBundleServiceTest {

    @Mock
    private UserKeyBundleRepository userKeyBundleRepository;
    @Mock
    private ConversationKeyBundleRepository conversationKeyBundleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private KeyBundleService keyBundleService;

    private User testUser;
    private UUID userId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User();
        testUser.setId(userId);
        testUser.setUsername("testuser");
    }

    @Test
    void uploadPublicKey_Success() {
        String publicKeyStr = "base64-jwk-public-key";
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userKeyBundleRepository.findByUserId(userId)).thenReturn(Optional.empty());

        UserKeyBundle mockSaved = UserKeyBundle.builder()
                .userId(userId)
                .publicKey(publicKeyStr)
                .build();
        when(userKeyBundleRepository.save(any())).thenReturn(mockSaved);

        UserKeyBundle result = keyBundleService.uploadPublicKey("testuser", publicKeyStr);

        assertNotNull(result);
        assertEquals(publicKeyStr, result.getPublicKey());
        verify(userKeyBundleRepository, times(1)).save(any());
    }

    @Test
    void getConversationMemberKeys_Success() {
        UUID conversationId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("otheruser");

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setMembers(Set.of(testUser, otherUser));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        UserKeyBundle bundle1 = UserKeyBundle.builder().userId(testUser.getId()).publicKey("key1").build();
        UserKeyBundle bundle2 = UserKeyBundle.builder().userId(otherUser.getId()).publicKey("key2").build();

        when(userKeyBundleRepository.findAllByUserIdIn(anyList()))
                .thenReturn(List.of(bundle1, bundle2));

        Map<UUID, String> keys = keyBundleService.getConversationMemberKeys(conversationId, "testuser");

        assertEquals(2, keys.size());
        assertEquals("key1", keys.get(testUser.getId()));
        assertEquals("key2", keys.get(otherUser.getId()));
    }

    @Test
    void getConversationMemberKeys_ThrowsIfNotMember() {
        UUID conversationId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setMembers(Set.of(otherUser)); // testUser is not a member

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        assertThrows(SecurityException.class, () -> 
            keyBundleService.getConversationMemberKeys(conversationId, "testuser")
        );
    }
}
