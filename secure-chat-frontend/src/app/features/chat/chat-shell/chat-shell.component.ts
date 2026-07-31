import { Component, OnInit, OnDestroy, ViewChild, ChangeDetectorRef, HostListener, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { GroupService } from '../../../core/services/group.service';
import { ConnectionService } from '../../../core/services/connection.service';
import { MessageService } from '../../../core/services/message.service';
import { FileUploadService } from '../../../core/services/file-upload.service';
import { GroupResponse, MessageResponse, TypingIndicatorPayload, RosterUpdatePayload, MessageReadPayload, WebSocketErrorPayload, ConnectionRequestResponse, PresencePayload } from '../../../shared/models';
import { ChatSidebarComponent } from '../chat-sidebar/chat-sidebar.component';
import { ChatHeaderComponent } from '../chat-header/chat-header.component';
import { MessageWindowComponent } from '../message-window/message-window.component';
import { MessageInputComponent, MessageInputPayload } from '../message-input/message-input.component';
import { EmptyStateComponent } from '../empty-state/empty-state.component';


/**
 * Smart Container Component — orchestrates the entire chat UI.
 *
 * Responsibilities:
 * - Loads group list from REST API
 * - Manages active conversation selection
 * - Subscribes/unsubscribes to WebSocket topics when conversation changes
 * - Subscribes to roster update events for all groups
 * - Re-syncs group list on WebSocket reconnection
 * - Handles file upload → send message flow
 * - Manages read receipts (send + receive)
 * - Manages pinned messages (load, pin, unpin)
 * - Passes data down to dumb children via @Input()
 * - Handles events emitted up via @Output()
 */
@Component({
  selector: 'app-chat-shell',
  standalone: true,
  imports: [
    CommonModule,
    ChatSidebarComponent,
    ChatHeaderComponent,
    MessageWindowComponent,
    MessageInputComponent,
    EmptyStateComponent,
  ],
  templateUrl: './chat-shell.component.html',
  styleUrl: './chat-shell.component.css',
})
export class ChatShellComponent implements OnInit, OnDestroy {
  @ViewChild(ChatSidebarComponent) sidebar!: ChatSidebarComponent;

  groups: GroupResponse[] = [];
  activeConversation: GroupResponse | null = null;
  messages: MessageResponse[] = [];
  typingUsers: string[] = [];
  pinnedMessages: MessageResponse[] = [];
  currentUsername = '';
  currentEmail = '';
  isProfilePopupOpen = false;
  isAdmin = false;

  /** Per-conversation unread message counts for sidebar badges. */
  unreadCounts = new Map<string, number>();

  /** Live username → online status map, updated by WebSocket presence events. */
  presenceMap = new Map<string, boolean>();

  /** URL security / WebSocket error toast (null = hidden). */
  wsError: string | null = null;
  private wsErrorTimeout: ReturnType<typeof setTimeout> | null = null;

  private subscriptions = new Subscription();
  private wsMessageSub?: Subscription;
  private wsTypingSub?: Subscription;
  private wsReadSub?: Subscription;
  private wsErrorSub?: Subscription;
  /** Roster subscriptions — one per loaded group. */
  private rosterSubs: Subscription[] = [];
  /** Global message subscriptions — one per conversation, for unread counting. */
  private globalMessageSubs: Subscription[] = [];
  private wsConnectionSub?: Subscription;
  private wsPresenceSub?: Subscription;

  constructor(
    private readonly authService: AuthService,
    private readonly wsService: WebSocketService,
    private readonly groupService: GroupService,
    private readonly messageService: MessageService,
    private readonly fileUploadService: FileUploadService,
    private readonly connectionService: ConnectionService,
    private readonly cdr: ChangeDetectorRef,
    private readonly elRef: ElementRef,
  ) {}

  /** Closes profile popup and expiry menu when clicking outside. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    // Close profile popup if clicking outside the avatar wrapper
    if (this.isProfilePopupOpen) {
      const avatarWrapper = this.elRef.nativeElement.querySelector('.util-avatar-wrapper');
      if (avatarWrapper && !avatarWrapper.contains(target)) {
        this.isProfilePopupOpen = false;
        this.cdr.markForCheck();
      }
    }
  }

  ngOnInit(): void {
    this.currentUsername = this.authService.getCurrentUsername();
    this.currentEmail = this.authService.getCurrentEmail();
    this.isAdmin = this.authService.isAdmin();

    // Connect WebSocket
    this.wsService.connect();

    // Load all conversations (GROUP + PRIVATE) and subscribe to roster + unread events
    this.subscriptions.add(
      this.groupService.getMyConversations().subscribe({
        next: (conversations) => {
          this.groups = conversations;
          this.subscribeToAllRosters();
          this.subscribeToAllGlobalMessages();
        },
        error: (err) => console.error('Failed to load conversations:', err),
      })
    );

    // Re-sync roster counts on WebSocket reconnection.
    // RxStomp automatically re-subscribes to STOMP topics, but
    // we may have missed roster events while disconnected.
    // Skip the first emission (initial connect) — only act on re-connects.
    let isFirstConnect = true;
    this.subscriptions.add(
      this.wsService.connected$.subscribe(() => {
        if (isFirstConnect) {
          isFirstConnect = false;
          return;
        }
        // Re-fetch group list to sync roster counts
        this.groupService.getMyConversations().subscribe({
          next: (groups) => {
            this.groups = groups;
            // Re-subscribe in case new groups appeared
            this.subscribeToAllRosters();
            // Also refresh the active conversation reference
            if (this.activeConversation) {
              this.activeConversation =
                this.groups.find(g => g.id === this.activeConversation!.id) ?? null;
            }
          },
          error: (err) => console.error('Failed to re-sync groups:', err),
        });
      })
    );

    // Subscribe to WebSocket errors (URL security blocks, etc.)
    this.wsErrorSub = this.wsService.watchErrors().subscribe({
      next: (error: WebSocketErrorPayload) => {
        this.showWsError(error);
      },
    });

    // Subscribe to real-time connection request notifications.
    // This is the critical fix for bidirectional visibility — previously the
    // sender's conversation list was never updated after acceptance.
    this.wsConnectionSub = this.wsService.watchConnectionRequests().subscribe({
      next: (request: ConnectionRequestResponse) => {
        if (request.status === 'ACCEPTED' && request.conversationId) {
          // A connection request was accepted — reload conversations so
          // the new private chat appears immediately for BOTH users.
          this.groupService.getMyConversations().subscribe({
            next: (conversations) => {
              this.groups = conversations;
              this.subscribeToAllRosters();
              this.subscribeToAllGlobalMessages();
              this.cdr.markForCheck();
            },
            error: (err) => console.error('Failed to reload conversations after acceptance:', err),
          });
        }
      },
    });

    // Subscribe to presence (online/offline) events for real-time status updates.
    // Updates the presenceMap so child components (sidebar search results, etc.)
    // can show live online/offline status without re-querying the REST API.
    this.wsPresenceSub = this.wsService.watchPresence().subscribe({
      next: (payload: PresencePayload) => {
        this.presenceMap = new Map(this.presenceMap).set(payload.username, payload.online);
        this.cdr.markForCheck();
      },
    });
  }

  /**
   * Called when the user clicks a conversation in the sidebar.
   * Loads message history and subscribes to real-time updates.
   */
  onConversationSelected(conversationId: string): void {
    const group = this.groups.find(g => g.id === conversationId);
    if (!group) return;

    this.activeConversation = group;
    this.messages = [];
    this.typingUsers = [];
    this.pinnedMessages = [];

    // Clear unread count for this conversation
    this.unreadCounts = new Map(this.unreadCounts);
    this.unreadCounts.delete(conversationId);

    // Unsubscribe from previous conversation's WebSocket topics
    this.wsMessageSub?.unsubscribe();
    this.wsTypingSub?.unsubscribe();
    this.wsReadSub?.unsubscribe();

    // Load message history via REST
    this.subscriptions.add(
      this.messageService.getHistory(conversationId).subscribe({
        next: (page) => {
          // API returns newest-first; reverse for chronological display
          this.messages = [...page.content].reverse();
          
          // Mark all unread messages as read in bulk
          if (this.activeConversation && this.activeConversation.id) {
            this.messageService.markConversationAsRead(this.activeConversation.id).subscribe({
              error: (err) => console.error('Failed to mark conversation as read:', err)
            });
          }
          
          this.cdr.markForCheck();
        },
        error: (err) => console.error('Failed to load messages:', err),
      })
    );

    // Load pinned messages via REST
    this.subscriptions.add(
      this.messageService.getPinnedMessages(conversationId).subscribe({
        next: (pinned) => {
          this.pinnedMessages = pinned;
        },
        error: (err) => console.error('Failed to load pinned messages:', err),
      })
    );

    // Subscribe to real-time messages via WebSocket
    this.wsMessageSub = this.wsService.watchConversation(conversationId).subscribe({
      next: (message) => {
        this.messages = [...this.messages, message];
        this.cdr.markForCheck();
        // Send read receipt for incoming messages from other users
        if (message.senderUsername !== this.currentUsername) {
          this.wsService.sendReadReceipt(conversationId, message.id);
        }
      },
    });

    // Subscribe to typing indicators via WebSocket
    this.wsTypingSub = this.wsService.watchTyping(conversationId).subscribe({
      next: (payload) => {
        if (payload.username === this.currentUsername) return;
        if (payload.typing) {
          if (!this.typingUsers.includes(payload.username)) {
            this.typingUsers = [...this.typingUsers, payload.username];
          }
        } else {
          this.typingUsers = this.typingUsers.filter(u => u !== payload.username);
        }
        this.cdr.markForCheck();
      },
    });

    // Subscribe to read receipt events via WebSocket
    this.wsReadSub = this.wsService.watchReadReceipts(conversationId).subscribe({
      next: (payload: MessageReadPayload) => {
        this.handleReadReceipt(payload);
        this.cdr.markForCheck();
      },
    });
  }

  /**
   * Called when a new group is created from the sidebar.
   * Prepends the group to the list and subscribes to its roster topic.
   */
  onGroupCreated(group: GroupResponse): void {
    this.groups = [group, ...this.groups];
    this.subscribeToRoster(group.id);
  }

  /**
   * Called when the user joins a group from the sidebar.
   * Prepends the group to the list and auto-selects it.
   */
  onGroupJoined(group: GroupResponse): void {
    // Avoid duplicates if the group was already listed
    if (!this.groups.find(g => g.id === group.id)) {
      this.groups = [group, ...this.groups];
      this.subscribeToRoster(group.id);
    }
    this.onConversationSelected(group.id);
  }

  /**
   * Called when a connection request is accepted from the sidebar.
   * Reloads conversations so the new private chat appears.
   */
  onConnectionAccepted(conversationId: string): void {
    this.groupService.getMyConversations().subscribe({
      next: (conversations) => {
        this.groups = conversations;
        this.subscribeToAllRosters();
        // Auto-select the new private chat
        this.onConversationSelected(conversationId);
      },
      error: (err) => console.error('Failed to reload conversations:', err),
    });
  }

  /**
   * Called when the user sends a message from the input component.
   * Handles optional file upload before sending the WebSocket message.
   */
  onMessageSent(payload: MessageInputPayload): void {
    if (!this.activeConversation) return;
    if (!payload.content.trim() && !payload.attachmentFile) return;

    const conversationId = this.activeConversation.id;

    if (payload.attachmentFile) {
      // Upload file first, then send message with attachment URL
      this.fileUploadService.upload(payload.attachmentFile).subscribe({
        next: (uploadResponse) => {
          this.wsService.sendMessage(conversationId, {
            content: payload.content.trim(),
            expiryMinutes: payload.expiryMinutes ?? null,
            attachmentUrl: uploadResponse.url,
            attachmentType: uploadResponse.contentType,
          });
        },
        error: (err) => console.error('File upload failed:', err),
      });
    } else {
      // Text-only message (with optional expiry)
      this.wsService.sendMessage(conversationId, {
        content: payload.content.trim(),
        expiryMinutes: payload.expiryMinutes ?? null,
      });
    }
  }

  /**
   * Called when the user's typing state changes.
   */
  onTypingChanged(isTyping: boolean): void {
    if (!this.activeConversation) return;
    this.wsService.sendTyping(this.activeConversation.id, isTyping);
  }

  // ════════════════════════════════════════════════════════════
  // Pin / Unpin
  // ════════════════════════════════════════════════════════════

  /**
   * Pins a message and refreshes the pinned messages list.
   */
  onPinMessage(messageId: string): void {
    this.messageService.pinMessage(messageId).subscribe({
      next: (updatedMessage) => {
        // Update the message in the main list
        this.messages = this.messages.map(m => m.id === messageId ? updatedMessage : m);
        // Refresh pinned list
        if (this.activeConversation) {
          this.messageService.getPinnedMessages(this.activeConversation.id).subscribe({
            next: (pinned) => this.pinnedMessages = pinned,
          });
        }
      },
      error: (err) => console.error('Failed to pin message:', err),
    });
  }

  /**
   * Unpins a message and refreshes the pinned messages list.
   */
  onUnpinMessage(messageId: string): void {
    this.messageService.unpinMessage(messageId).subscribe({
      next: (updatedMessage) => {
        // Update the message in the main list
        this.messages = this.messages.map(m => m.id === messageId ? updatedMessage : m);
        // Refresh pinned list
        if (this.activeConversation) {
          this.messageService.getPinnedMessages(this.activeConversation.id).subscribe({
            next: (pinned) => this.pinnedMessages = pinned,
          });
        }
      },
      error: (err) => console.error('Failed to unpin message:', err),
    });
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.wsMessageSub?.unsubscribe();
    this.wsTypingSub?.unsubscribe();
    this.wsReadSub?.unsubscribe();
    this.wsErrorSub?.unsubscribe();
    this.wsConnectionSub?.unsubscribe();
    this.wsPresenceSub?.unsubscribe();
    if (this.wsErrorTimeout) clearTimeout(this.wsErrorTimeout);
    this.unsubscribeAllRosters();
    this.unsubscribeAllGlobalMessages();
    this.wsService.disconnect();
  }

  // ════════════════════════════════════════════════════════════
  // Read receipt handling
  // ════════════════════════════════════════════════════════════

  /**
   * Updates the local message list when a read receipt is received.
   * Adds the read receipt to the matching message's readReceipts array.
   */
  private handleReadReceipt(payload: MessageReadPayload): void {
    if (!payload.userId || !payload.username || !payload.readAt) return;

    this.messages = this.messages.map(msg => {
      if (msg.id !== payload.messageId) return msg;

      // Avoid duplicate read receipts
      const alreadyRead = msg.readReceipts?.some(r => r.userId === payload.userId);
      if (alreadyRead) return msg;

      return {
        ...msg,
        readReceipts: [
          ...(msg.readReceipts ?? []),
          {
            userId: payload.userId!,
            username: payload.username!,
            readAt: payload.readAt!,
          },
        ],
      };
    });
  }

  // ════════════════════════════════════════════════════════════
  // Roster subscription management
  // ════════════════════════════════════════════════════════════

  /**
   * Subscribes to roster update events for all loaded groups.
   * Tears down existing roster subscriptions first to avoid duplicates.
   */
  private subscribeToAllRosters(): void {
    this.unsubscribeAllRosters();
    for (const group of this.groups) {
      this.subscribeToRoster(group.id);
    }
  }

  /**
   * Subscribes to roster updates for a single group.
   * On each event, patches the specific group's memberCount and memberUsernames
   * in place — does NOT refetch the entire group list.
   */
  private subscribeToRoster(groupId: string): void {
    const sub = this.wsService.watchRoster(groupId).subscribe({
      next: (payload: RosterUpdatePayload) => {
        this.groups = this.groups.map(g =>
          g.id === payload.conversationId
            ? { ...g, memberCount: payload.memberCount, memberUsernames: payload.memberUsernames }
            : g
        );
        // Also update the active conversation reference if it's the same group
        if (this.activeConversation?.id === payload.conversationId) {
          this.activeConversation =
            this.groups.find(g => g.id === payload.conversationId) ?? this.activeConversation;
        }
        this.cdr.markForCheck();
      },
    });
    this.rosterSubs.push(sub);
  }

  /**
   * Tears down all active roster subscriptions.
   */
  private unsubscribeAllRosters(): void {
    for (const sub of this.rosterSubs) {
      sub.unsubscribe();
    }
    this.rosterSubs = [];
  }

  // ════════════════════════════════════════════════════════════
  // Global message subscriptions (for unread counting)
  // ════════════════════════════════════════════════════════════

  /**
   * Subscribes to real-time messages for ALL conversations.
   * When a message arrives for a non-active conversation, increments
   * the unread counter for that conversation in the sidebar badge.
   */
  private subscribeToAllGlobalMessages(): void {
    this.unsubscribeAllGlobalMessages();
    for (const group of this.groups) {
      this.subscribeToGlobalMessages(group.id);
    }
  }

  private subscribeToGlobalMessages(conversationId: string): void {
    const sub = this.wsService.watchConversation(conversationId).subscribe({
      next: (message) => {
        // Only count messages from OTHER users in NON-active conversations
        if (
          message.senderUsername !== this.currentUsername &&
          this.activeConversation?.id !== conversationId
        ) {
          const current = this.unreadCounts.get(conversationId) ?? 0;
          this.unreadCounts = new Map(this.unreadCounts).set(conversationId, current + 1);
          this.cdr.markForCheck();
        }
      },
    });
    this.globalMessageSubs.push(sub);
  }

  private unsubscribeAllGlobalMessages(): void {
    for (const sub of this.globalMessageSubs) {
      sub.unsubscribe();
    }
    this.globalMessageSubs = [];
  }

  // ════════════════════════════════════════════════════════════
  // WebSocket error display
  // ════════════════════════════════════════════════════════════

  /**
   * Surfaces a WebSocket error as a toast notification.
   * Auto-dismisses after 6 seconds.
   */
  private showWsError(error: WebSocketErrorPayload): void {
    let msg = error.message;

    // For UNSAFE_URL errors, append the blocked URL details
    if (error.errorType === 'UNSAFE_URL' && error.details?.length) {
      const urlDetails = error.details
        .map(d => `${d.url} — ${d.reason}`)
        .join('; ');
      msg = `🛡️ Message blocked: ${urlDetails}`;
    } else if (error.errorType === 'FORBIDDEN') {
      msg = `🔒 ${error.message}`;
    } else if (error.errorType === 'NOT_FOUND') {
      msg = `⚠️ ${error.message}`;
    } else if (error.errorType === 'INTERNAL_ERROR') {
      msg = `❌ ${error.message}`;
    }

    this.wsError = msg;

    // Auto-dismiss after 6 seconds
    if (this.wsErrorTimeout) clearTimeout(this.wsErrorTimeout);
    this.wsErrorTimeout = setTimeout(() => {
      this.wsError = null;
      this.cdr.markForCheck();
    }, 6000);
    this.cdr.markForCheck();
  }

  /** Manually dismiss the error toast. */
  dismissWsError(): void {
    this.wsError = null;
    if (this.wsErrorTimeout) {
      clearTimeout(this.wsErrorTimeout);
      this.wsErrorTimeout = null;
    }
  }

  /** Toggles the profile popup card above the avatar */
  toggleProfilePopup(): void {
    this.isProfilePopupOpen = !this.isProfilePopupOpen;
  }

  onLogout(): void {
    this.wsService.disconnect();
    this.authService.logout();
  }
}
