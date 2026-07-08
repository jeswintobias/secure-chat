package com.securechat.controller;

import com.securechat.dto.request.CreateGroupRequest;
import com.securechat.dto.request.JoinGroupRequest;
import com.securechat.dto.response.GroupResponse;
import com.securechat.dto.response.UserResponse;
import com.securechat.dto.websocket.RosterUpdatePayload;
import com.securechat.dto.websocket.RosterUpdatePayload.RosterEventType;
import com.securechat.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for group conversation management.
 *
 * All endpoints require authentication (enforced by {@link com.securechat.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Creates a new group conversation.
     * The authenticated user is automatically added as the first member.
     *
     * @param request   the validated group creation request
     * @param principal the authenticated user
     * @return 201 Created with the new GroupResponse
     */
    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Principal principal
    ) {
        GroupResponse response = groupService.createGroup(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Joins an existing group via referral code.
     *
     * After a successful join, broadcasts a roster update event to all
     * subscribers of /topic/conversation/{groupId}/roster so that
     * existing members see the member count update in real time.
     *
     * @param groupId   the group conversation ID
     * @param request   the join request containing the referral code
     * @param principal the authenticated user
     * @return 200 OK with the updated GroupResponse
     */
    @PostMapping("/{groupId}/join")
    public ResponseEntity<GroupResponse> joinGroup(
            @PathVariable UUID groupId,
            @Valid @RequestBody JoinGroupRequest request,
            Principal principal
    ) {
        GroupResponse response = groupService.joinGroup(groupId, request.getReferralCode(), principal.getName());

        // Broadcast roster update to all WebSocket subscribers of this group
        broadcastRosterUpdate(groupId, response, principal.getName(), RosterEventType.JOIN);

        return ResponseEntity.ok(response);
    }

    /**
     * Removes a member from a group.
     *
     * After a successful removal, broadcasts a roster update event.
     *
     * @param groupId the group conversation ID
     * @param userId  the UUID of the user to remove
     * @return 204 No Content on success
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId
    ) {
        String removedUsername = groupService.removeMember(groupId, userId);

        // Re-fetch updated group state for the broadcast
        GroupResponse updatedGroup = groupService.getGroupResponse(groupId);
        broadcastRosterUpdate(groupId, updatedGroup, removedUsername, RosterEventType.LEAVE);

        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all members of a group.
     *
     * @param groupId the group conversation ID
     * @return 200 OK with list of UserResponse DTOs
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<UserResponse>> getGroupMembers(@PathVariable UUID groupId) {
        List<UserResponse> members = groupService.getGroupMembers(groupId);
        return ResponseEntity.ok(members);
    }

    /**
     * Lists all available groups.
     *
     * @return 200 OK with list of GroupResponse DTOs
     */
    @GetMapping
    public ResponseEntity<List<GroupResponse>> listGroups() {
        List<GroupResponse> groups = groupService.listAllGroups();
        return ResponseEntity.ok(groups);
    }

    /**
     * Lists all conversations (GROUP and PRIVATE) for the authenticated user.
     *
     * <p>For PRIVATE conversations, the name is resolved to the other user's username.
     *
     * @param principal the authenticated user
     * @return 200 OK with list of GroupResponse DTOs
     */
    @GetMapping("/my-conversations")
    public ResponseEntity<List<GroupResponse>> getMyConversations(Principal principal) {
        List<GroupResponse> conversations = groupService.getUserConversations(principal.getName());
        return ResponseEntity.ok(conversations);
    }

    // ======================== Internal Helpers ========================

    /**
     * Broadcasts a roster update event to /topic/conversation/{groupId}/roster.
     * Follows the same topic naming convention as messages and typing indicators.
     */
    private void broadcastRosterUpdate(UUID groupId, GroupResponse group,
                                        String changedUsername, RosterEventType eventType) {
        RosterUpdatePayload payload = RosterUpdatePayload.builder()
                .conversationId(groupId)
                .memberCount(group.getMemberCount())
                .memberUsernames(group.getMemberUsernames())
                .changedUsername(changedUsername)
                .eventType(eventType)
                .build();

        messagingTemplate.convertAndSend(
                "/topic/conversation/" + groupId + "/roster",
                payload
        );
    }
}
