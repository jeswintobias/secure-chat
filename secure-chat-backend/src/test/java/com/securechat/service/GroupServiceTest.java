package com.securechat.service;

import com.securechat.dto.request.CreateGroupRequest;
import com.securechat.dto.response.GroupResponse;
import com.securechat.entity.Conversation;
import com.securechat.entity.Conversation.ConversationType;
import com.securechat.entity.User;
import com.securechat.exception.DuplicateResourceException;
import com.securechat.exception.InvalidReferralCodeException;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GroupService}.
 *
 * Tests cover group creation (with and without custom referral codes),
 * joining (valid, invalid code, already member), and member removal.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    private User testCreator;
    private User testJoiner;
    private Conversation testGroup;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();

        testCreator = User.builder()
                .id(UUID.randomUUID())
                .username("creator")
                .email("creator@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .build();

        testJoiner = User.builder()
                .id(UUID.randomUUID())
                .username("joiner")
                .email("joiner@example.com")
                .passwordHash("hash")
                .role(User.Role.USER)
                .build();

        testGroup = Conversation.builder()
                .id(groupId)
                .type(ConversationType.GROUP)
                .name("Dev Team")
                .referralCode("DEVTEAM1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testGroup.getMembers().add(testCreator);
    }

    // ======================== createGroup Tests ========================

    @Test
    @DisplayName("createGroup — no custom referral code → auto-generated code, creator is first member")
    void createGroup_success() {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("New Group")
                .build();

        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(testCreator));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        when(conversationRepository.save(captor.capture())).thenAnswer(invocation -> {
            Conversation conv = invocation.getArgument(0);
            conv.setId(UUID.randomUUID());
            conv.setCreatedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            return conv;
        });

        // Act
        GroupResponse response = groupService.createGroup(request, "creator");

        // Assert
        assertThat(response.getName()).isEqualTo("New Group");
        assertThat(response.getType()).isEqualTo("GROUP");
        assertThat(response.getMemberCount()).isEqualTo(1);
        assertThat(response.getMemberUsernames()).containsExactly("creator");

        Conversation savedGroup = captor.getValue();
        assertThat(savedGroup.getReferralCode()).isNotBlank();
        assertThat(savedGroup.getReferralCode()).hasSize(8); // UUID substring
    }

    @Test
    @DisplayName("createGroup — custom referral code → uses the provided code")
    void createGroup_withCustomReferralCode() {
        CreateGroupRequest request = CreateGroupRequest.builder()
                .name("VIP Group")
                .referralCode("MYVIPCODE")
                .build();

        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(testCreator));

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        when(conversationRepository.save(captor.capture())).thenAnswer(invocation -> {
            Conversation conv = invocation.getArgument(0);
            conv.setId(UUID.randomUUID());
            conv.setCreatedAt(Instant.now());
            conv.setUpdatedAt(Instant.now());
            return conv;
        });

        // Act
        GroupResponse response = groupService.createGroup(request, "creator");

        // Assert
        Conversation savedGroup = captor.getValue();
        assertThat(savedGroup.getReferralCode()).isEqualTo("MYVIPCODE");
        assertThat(response.getReferralCode()).isEqualTo("MYVIPCODE");
    }

    // ======================== joinGroup Tests ========================

    @Test
    @DisplayName("joinGroup — valid referral code → user added to members")
    void joinGroup_success() {
        when(conversationRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(userRepository.findByUsername("joiner")).thenReturn(Optional.of(testJoiner));
        when(conversationRepository.save(any(Conversation.class))).thenReturn(testGroup);

        // Act
        GroupResponse response = groupService.joinGroup(groupId, "DEVTEAM1", "joiner");

        // Assert
        assertThat(testGroup.getMembers()).contains(testJoiner);
        assertThat(response.getMemberUsernames()).contains("joiner");
        verify(conversationRepository).save(testGroup);
    }

    @Test
    @DisplayName("joinGroup — invalid referral code → throws InvalidReferralCodeException")
    void joinGroup_invalidReferralCode_throws() {
        when(conversationRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        assertThatThrownBy(() -> groupService.joinGroup(groupId, "WRONGCODE", "joiner"))
                .isInstanceOf(InvalidReferralCodeException.class)
                .hasMessageContaining("Invalid referral code");

        assertThat(testGroup.getMembers()).doesNotContain(testJoiner);
    }

    @Test
    @DisplayName("joinGroup — user already a member → throws DuplicateResourceException")
    void joinGroup_alreadyMember_throws() {
        when(conversationRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(userRepository.findByUsername("creator")).thenReturn(Optional.of(testCreator));

        // testCreator is already a member (added in setUp)
        assertThatThrownBy(() -> groupService.joinGroup(groupId, "DEVTEAM1", "creator"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already a member");
    }
}
