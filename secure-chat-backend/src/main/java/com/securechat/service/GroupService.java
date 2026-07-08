package com.securechat.service;

import com.securechat.dto.request.CreateGroupRequest;
import com.securechat.dto.response.GroupResponse;
import com.securechat.dto.response.UserResponse;
import com.securechat.entity.Conversation;
import com.securechat.entity.Conversation.ConversationType;
import com.securechat.entity.User;
import com.securechat.exception.DuplicateResourceException;
import com.securechat.exception.InvalidReferralCodeException;
import com.securechat.exception.ResourceNotFoundException;
import com.securechat.repository.ConversationRepository;
import com.securechat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for group conversation management.
 *
 * <p>Handles group creation, joining via referral code,
 * member removal, and group listing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new group conversation.
     *
     * <p>The creating user is automatically added as the first member.
     * If no referral code is provided, a random UUID-based code is generated.
     *
     * @param request        the group creation request DTO
     * @param creatorUsername the username of the group creator
     * @return the created group as a DTO
     */
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + creatorUsername)
                );

        // Auto-generate referral code if not provided
        String referralCode = (request.getReferralCode() != null && !request.getReferralCode().isBlank())
                ? request.getReferralCode()
                : UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Conversation group = Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.getName())
                .referralCode(referralCode)
                .build();

        // Creator is automatically the first member
        group.getMembers().add(creator);

        Conversation saved = conversationRepository.save(group);
        log.info("Group '{}' created by {} with referral code: {}", saved.getName(), creatorUsername, referralCode);

        return toGroupResponse(saved);
    }

    /**
     * Adds a user to a group conversation via referral code.
     *
     * @param groupId        the group conversation ID
     * @param referralCode   the referral code to validate
     * @param username       the username of the user joining
     * @return the updated group as a DTO
     * @throws InvalidReferralCodeException if the code doesn't match
     * @throws DuplicateResourceException   if the user is already a member
     */
    @Transactional
    public GroupResponse joinGroup(UUID groupId, String referralCode, String username) {
        Conversation group = conversationRepository.findById(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Group not found: " + groupId)
                );

        // Validate conversation is a GROUP type
        if (group.getType() != ConversationType.GROUP) {
            throw new IllegalArgumentException("Conversation " + groupId + " is not a group");
        }

        // Validate referral code
        if (!group.getReferralCode().equals(referralCode)) {
            throw new InvalidReferralCodeException("Invalid referral code for group: " + group.getName());
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + username)
                );

        // Check for duplicate membership
        boolean alreadyMember = group.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
        if (alreadyMember) {
            throw new DuplicateResourceException("User " + username + " is already a member of this group");
        }

        group.getMembers().add(user);
        conversationRepository.save(group);
        log.info("User '{}' joined group '{}'", username, group.getName());

        return toGroupResponse(group);
    }

    /**
     * Removes a user from a group conversation.
     *
     * @param groupId  the group conversation ID
     * @param userId   the UUID of the user to remove
     * @return the username of the removed user
     */
    @Transactional
    public String removeMember(UUID groupId, UUID userId) {
        Conversation group = conversationRepository.findById(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Group not found: " + groupId)
                );

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + userId)
                );

        boolean removed = group.getMembers().removeIf(m -> m.getId().equals(userId));
        if (!removed) {
            throw new ResourceNotFoundException("User is not a member of this group");
        }

        conversationRepository.save(group);
        log.info("User '{}' removed from group '{}'", user.getUsername(), group.getName());
        return user.getUsername();
    }

    /**
     * Returns a single group as a DTO.
     * Used by the controller to broadcast updated roster state.
     *
     * @param groupId the group conversation ID
     * @return the group as a GroupResponse DTO
     */
    @Transactional(readOnly = true)
    public GroupResponse getGroupResponse(UUID groupId) {
        Conversation group = conversationRepository.findById(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Group not found: " + groupId)
                );
        return toGroupResponse(group);
    }

    /**
     * Lists all members of a group conversation as DTOs.
     *
     * @param groupId the group conversation ID
     * @return list of UserResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getGroupMembers(UUID groupId) {
        Conversation group = conversationRepository.findById(groupId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Group not found: " + groupId)
                );

        return group.getMembers().stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lists all group conversations.
     *
     * @return list of GroupResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> listAllGroups() {
        return conversationRepository.findAllByType(ConversationType.GROUP).stream()
                .map(this::toGroupResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lists all conversations (GROUP and PRIVATE) that a user is a member of.
     *
     * <p>For PRIVATE conversations, the name is resolved to the other user's username
     * so the frontend can display it properly in the sidebar.
     *
     * @param username the username of the user
     * @return list of GroupResponse DTOs (reused for both types)
     */
    @Transactional(readOnly = true)
    public List<GroupResponse> getUserConversations(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found: " + username)
                );

        return conversationRepository.findAllByMemberId(user.getId()).stream()
                .map(conversation -> {
                    if (conversation.getType() == ConversationType.PRIVATE) {
                        return toPrivateConversationResponse(conversation, username);
                    }
                    return toGroupResponse(conversation);
                })
                .collect(Collectors.toList());
    }

    // ======================== Entity-to-DTO Conversion ========================

    private GroupResponse toGroupResponse(Conversation conversation) {
        return GroupResponse.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .type(conversation.getType().name())
                .referralCode(conversation.getReferralCode())
                .memberCount(conversation.getMembers().size())
                .memberUsernames(
                        conversation.getMembers().stream()
                                .map(User::getUsername)
                                .collect(Collectors.toList())
                )
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    /**
     * Converts a PRIVATE conversation to a GroupResponse, resolving the
     * conversation name to the other user's username.
     */
    private GroupResponse toPrivateConversationResponse(Conversation conversation, String currentUsername) {
        // Find the other user's username for display
        String otherUsername = conversation.getMembers().stream()
                .map(User::getUsername)
                .filter(name -> !name.equals(currentUsername))
                .findFirst()
                .orElse("Unknown User");

        return GroupResponse.builder()
                .id(conversation.getId())
                .name(otherUsername)
                .type(conversation.getType().name())
                .referralCode(null)
                .memberCount(conversation.getMembers().size())
                .memberUsernames(
                        conversation.getMembers().stream()
                                .map(User::getUsername)
                                .collect(Collectors.toList())
                )
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .online(user.isOnlineStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

