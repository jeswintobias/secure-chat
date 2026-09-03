package com.securechat.service;

import com.securechat.dto.response.MessageReadDto;
import com.securechat.dto.response.MessageResponse;
import com.securechat.entity.ChatMessage;
import com.securechat.entity.Conversation;
import com.securechat.entity.MessageRead;
import com.securechat.entity.User;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.ChatMessageRepository;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.MessageReadRepository;
import com.securechat.repository.UserRepository;
import com.securechat.repository.MessageReactionRepository;
import com.securechat.service.urlsecurity.UrlScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MessageService}.
 *
 * Tests cover message sending (text, attachments, ephemeral),
 * message pinning, and read receipts.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageReadRepository messageReadRepository;
    @Mock private MessageReactionRepository reactionRepository;
    @Mock private XssSanitizer xssSanitizer;
    @Mock private UrlSecurityService urlSecurityService;

    @InjectMocks
    private MessageService messageService;

    private User testSender;
    private Conversation testConversation;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        conversationId = UUID.randomUUID();

        testSender = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .build();

        testConversation = Conversation.builder()
                .id(conversationId)
                .type(Conversation.ConversationType.GROUP)
                .name("Test Group")
                .referralCode("TESTCODE")
                .build();
        testConversation.getMembers().add(testSender);
    }

    /**
     * Helper: creates a safe UrlScanResult stub.
     */
    private UrlScanResult safeScanResult(String content) {
        return UrlScanResult.builder().processedContent(content).build();
    }

    // ======================== sendMessage Tests ========================

    @Test
    @DisplayName("sendMessage — text only → sanitized content persisted, MessageResponse returned")
    void sendMessage_textOnly_success() {
        // Arrange
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testSender));
        when(xssSanitizer.sanitize("Hello world")).thenReturn("Hello world");
        when(urlSecurityService.scanMessageContent("Hello world")).thenReturn(safeScanResult("Hello world"));

        ChatMessage savedMessage = ChatMessage.builder()
                .id(UUID.randomUUID())
                .conversation(testConversation)
                .sender(testSender)
                .content("Hello world")
                .messageType(ChatMessage.MessageType.TEXT)
                .createdAt(Instant.now())
                .build();
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMessage);
        when(messageReadRepository.findByMessageId(any())).thenReturn(List.of());
        when(reactionRepository.findByMessageIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        // Act
        MessageResponse response = messageService.sendMessage(conversationId, "alice", "Hello world", null);

        // Assert
        assertThat(response.getContent()).isEqualTo("Hello world");
        assertThat(response.getSenderUsername()).isEqualTo("alice");
        assertThat(response.getMessageType()).isEqualTo("TEXT");
        assertThat(response.getExpiresAt()).isNull();

        verify(messageRepository).save(any(ChatMessage.class));
    }

    @Test
    @DisplayName("sendMessage — expiryMinutes > 0 → expiresAt is set in the future")
    void sendMessage_withExpiry_setsExpiresAt() {
        // Arrange
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testSender));
        when(xssSanitizer.sanitize("Ephemeral message")).thenReturn("Ephemeral message");
        when(urlSecurityService.scanMessageContent("Ephemeral message"))
                .thenReturn(safeScanResult("Ephemeral message"));
        when(messageReadRepository.findByMessageId(any())).thenReturn(List.of());
        when(reactionRepository.findByMessageIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        // Capture the saved message to verify expiresAt
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(messageRepository.save(captor.capture())).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        // Act
        messageService.sendMessage(conversationId, "alice", "Ephemeral message", 30);

        // Assert
        ChatMessage captured = captor.getValue();
        assertThat(captured.getExpiresAt()).isNotNull();
        assertThat(captured.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("sendMessage — conversation not found → throws ResourceNotFoundException")
    void sendMessage_conversationNotFound_throws() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(conversationId, "alice", "Hello", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Conversation not found");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage — user not a member of conversation → throws IllegalArgumentException")
    void sendMessage_userNotMember_throws() {
        // Create a different user who is NOT in the conversation
        User outsider = User.builder()
                .id(UUID.randomUUID())
                .username("bob")
                .email("bob@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .build();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(outsider));

        assertThatThrownBy(() -> messageService.sendMessage(conversationId, "bob", "Hello", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a member");

        verify(messageRepository, never()).save(any());
    }

    @Test
    @DisplayName("sendMessage — image attachment → messageType set to IMAGE")
    void sendMessage_imageAttachment_setsTypeIMAGE() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(testConversation));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testSender));
        when(xssSanitizer.sanitize("Check this photo")).thenReturn("Check this photo");
        when(urlSecurityService.scanMessageContent("Check this photo"))
                .thenReturn(safeScanResult("Check this photo"));
        when(urlSecurityService.validateSingleUrl("/api/upload/files/photo.png"))
                .thenReturn(safeScanResult("/api/upload/files/photo.png"));
        when(messageReadRepository.findByMessageId(any())).thenReturn(List.of());
        when(reactionRepository.findByMessageIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        when(messageRepository.save(captor.capture())).thenAnswer(invocation -> {
            ChatMessage msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        // Act
        messageService.sendMessage(
                conversationId, "alice", "Check this photo", null,
                "/api/upload/files/photo.png", "image/png", "photo.png"
        );

        // Assert
        ChatMessage captured = captor.getValue();
        assertThat(captured.getMessageType()).isEqualTo(ChatMessage.MessageType.IMAGE);
        assertThat(captured.getAttachmentUrl()).isEqualTo("/api/upload/files/photo.png");
        assertThat(captured.getOriginalName()).isEqualTo("photo.png");
    }

    // ======================== Pin Tests ========================

    @Test
    @DisplayName("pinMessage — message found → pinned=true, pinnedBy set")
    void pinMessage_success() {
        UUID messageId = UUID.randomUUID();
        ChatMessage existingMessage = ChatMessage.builder()
                .id(messageId)
                .conversation(testConversation)
                .sender(testSender)
                .content("Important announcement")
                .messageType(ChatMessage.MessageType.TEXT)
                .pinned(false)
                .createdAt(Instant.now())
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageReadRepository.findByMessageId(any())).thenReturn(List.of());
        when(reactionRepository.findByMessageIdOrderByCreatedAtAsc(any())).thenReturn(List.of());

        // Act
        MessageResponse response = messageService.pinMessage(messageId, "admin");

        // Assert
        assertThat(response.isPinned()).isTrue();
        assertThat(response.getPinnedBy()).isEqualTo("admin");
        assertThat(response.getPinnedAt()).isNotNull();
    }

    // ======================== Read Receipt Tests ========================

    @Test
    @DisplayName("markAsRead — no existing receipt → creates new MessageRead")
    void markAsRead_newRead_createsReceipt() {
        UUID messageId = UUID.randomUUID();
        User reader = User.builder()
                .id(UUID.randomUUID())
                .username("bob")
                .email("bob@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .build();

        ChatMessage message = ChatMessage.builder()
                .id(messageId)
                .conversation(testConversation)
                .sender(testSender)
                .content("Read me")
                .messageType(ChatMessage.MessageType.TEXT)
                .createdAt(Instant.now())
                .build();

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(reader));
        when(messageReadRepository.existsByMessageIdAndUserId(messageId, reader.getId())).thenReturn(false);
        when(messageReadRepository.save(any(MessageRead.class))).thenAnswer(invocation -> {
            MessageRead read = invocation.getArgument(0);
            read.setId(UUID.randomUUID());
            read.setReadAt(Instant.now());
            return read;
        });

        // Act
        MessageReadDto result = messageService.markAsRead(messageId, "bob");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("bob");
        assertThat(result.getUserId()).isEqualTo(reader.getId());

        verify(messageReadRepository).save(any(MessageRead.class));
    }
}
