import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ChangeDetectorRef, OnInit, OnDestroy, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { GroupResponse, ConnectionRequestResponse, UserResponse } from '../../../shared/models';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { AuthService } from '../../../core/services/auth.service';
import { GroupService } from '../../../core/services/group.service';
import { ConnectionService } from '../../../core/services/connection.service';
import { WebSocketService } from '../../../core/services/websocket.service';
import { UserService } from '../../../core/services/user.service';

/**
 * Sidebar view states for the overlay panel.
 * - 'none': Normal sidebar, no overlay
 * - 'create': Create Group form
 * - 'join': Join Group form
 * - 'created': Post-creation confirmation card with referral code
 */
type SidebarOverlay = 'none' | 'create' | 'join' | 'created' | 'add-friend' | 'pending' | 'settings' | 'delete-confirm';

/** localStorage key for pinned chat IDs. */
const PINNED_CHATS_KEY = 'securechat_pinned_chats';

@Component({
  selector: 'app-chat-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, RelativeTimePipe],
  templateUrl: './chat-sidebar.component.html',
  styleUrl: './chat-sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatSidebarComponent implements OnInit, OnDestroy {
  @Input() conversations: GroupResponse[] = [];
  @Input() activeConversationId = '';
  @Input() currentUsername = '';
  /** Live username → online status map from WebSocket presence events. */
  @Input() presenceMap = new Map<string, boolean>();
  /** Per-conversation unread message counts for badge display. */
  @Input() unreadCounts = new Map<string, number>();
  @Output() conversationSelected = new EventEmitter<string>();
  @Output() groupCreated = new EventEmitter<GroupResponse>();
  @Output() groupJoined = new EventEmitter<GroupResponse>();
  @Output() connectionAccepted = new EventEmitter<string>(); // emits conversationId

  searchQuery = '';

  // ── Overlay state ──
  overlay: SidebarOverlay = 'none';
  isFabMenuOpen = false;

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: Event): void {
    const target = event.target as HTMLElement;
    if (this.isFabMenuOpen && !target.closest('.fab-container')) {
      this.isFabMenuOpen = false;
      this.cdr.markForCheck();
    }
  }

  toggleFabMenu(): void {
    this.isFabMenuOpen = !this.isFabMenuOpen;
  }

  // ── Create Group form ──
  newGroupName = '';
  createError = '';
  isCreating = false;

  // ── Join Group form ──
  joinGroupId = '';
  joinReferralCode = '';
  joinError = '';
  isJoining = false;

  // ── Post-creation confirmation ──
  createdGroup: GroupResponse | null = null;
  copied = false;
  private copiedTimeout: ReturnType<typeof setTimeout> | null = null;

  // ── Pinned Chats ──
  pinnedChatIds: Set<string>;

  // ── Add Friend (user search) ──
  friendSearchQuery = '';
  friendSearchResults: UserResponse[] = [];
  friendSearchLoading = false;
  friendSendSuccess = '';
  friendSendError = '';
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  // ── Pending Requests ──
  pendingRequests: ConnectionRequestResponse[] = [];
  pendingLoading = false;
  pendingCount = 0;
  private wsSub: Subscription | null = null;

  // ── Settings ──
  settingsLoading = false;
  settingsError = '';
  settingsSuccess = '';
  lastSeenPrivacy: 'EVERYONE' | 'CONTACTS' | 'NOBODY' = 'EVERYONE';
  readReceiptsEnabled = true;

  constructor(
    private readonly authService: AuthService,
    private readonly groupService: GroupService,
    private readonly connectionService: ConnectionService,
    private readonly wsService: WebSocketService,
    private readonly userService: UserService,
    private readonly cdr: ChangeDetectorRef,
  ) {
    // Load pinned chats from localStorage
    this.pinnedChatIds = this.loadPinnedChats();
  }

  ngOnInit(): void {
    // Initial fetch to set the badge count
    this.connectionService.getPendingRequests().subscribe({
      next: (requests) => {
        this.pendingCount = requests.length;
        this.cdr.markForCheck();
      }
    });

    // Listen for real-time connection requests
    this.wsSub = this.wsService.watchConnectionRequests().subscribe({
      next: (request) => {
        if (request.status === 'PENDING') {
          this.pendingCount++;
          // Dynamically add to the list if the dialog is open
          if (this.overlay === 'pending') {
            if (!this.pendingRequests.some(r => r.id === request.id)) {
              this.pendingRequests = [request, ...this.pendingRequests];
            }
          }
          this.cdr.markForCheck();
        }
      }
    });
  }

  ngOnDestroy(): void {
    if (this.wsSub) {
      this.wsSub.unsubscribe();
    }
  }

  /**
   * Returns conversations sorted: pinned first (by recency), then unpinned (by recency).
   * Filtered by search query if present.
   */
  get filteredConversations(): GroupResponse[] {
    let list = this.conversations;

    // Apply search filter
    if (this.searchQuery.trim()) {
      const q = this.searchQuery.toLowerCase();
      list = list.filter(c => c.name.toLowerCase().includes(q));
    }

    // Sort: pinned first, then by updatedAt descending within each group
    return [...list].sort((a, b) => {
      const aPinned = this.pinnedChatIds.has(a.id);
      const bPinned = this.pinnedChatIds.has(b.id);

      if (aPinned && !bPinned) return -1;
      if (!aPinned && bPinned) return 1;

      // Within the same group (both pinned or both unpinned), sort by recency
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
    });
  }

  /**
   * Returns true if the filtered list has any pinned conversations
   * AND any unpinned conversations (so we can show the divider).
   */
  get showPinnedDivider(): boolean {
    const filtered = this.filteredConversations;
    const hasPinned = filtered.some(c => this.pinnedChatIds.has(c.id));
    const hasUnpinned = filtered.some(c => !this.pinnedChatIds.has(c.id));
    return hasPinned && hasUnpinned;
  }

  /** Index of the first unpinned conversation in the filtered list. */
  get dividerIndex(): number {
    return this.filteredConversations.findIndex(c => !this.pinnedChatIds.has(c.id));
  }

  isPinned(conversationId: string): boolean {
    return this.pinnedChatIds.has(conversationId);
  }

  togglePinChat(conversationId: string, event: Event): void {
    event.stopPropagation(); // Don't select the conversation
    if (this.pinnedChatIds.has(conversationId)) {
      this.pinnedChatIds.delete(conversationId);
    } else {
      this.pinnedChatIds.add(conversationId);
    }
    this.savePinnedChats();
    this.cdr.markForCheck();
  }

  getInitials(name: string): string {
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
  }

  getAvatarColor(name: string): string {
    const colors = ['#00a884', '#53bdeb', '#ff6b6b', '#ffa726', '#7c4dff', '#e91e63', '#009688'];
    let hash = 0;
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }

  selectConversation(id: string): void {
    this.conversationSelected.emit(id);
  }

  onLogout(): void {
    this.authService.logout();
  }

  // ── Overlay controls ──

  openSettingsDialog(): void {
    this.overlay = 'settings';
    this.settingsLoading = true;
    this.settingsError = '';
    this.settingsSuccess = '';
    
    this.userService.getCurrentUser().subscribe({
      next: (user) => {
        this.lastSeenPrivacy = user.lastSeenPrivacy || 'EVERYONE';
        this.readReceiptsEnabled = user.readReceiptsEnabled ?? true;
        this.settingsLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.settingsError = 'Failed to load settings';
        this.settingsLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  openCreateDialog(): void {
    this.isFabMenuOpen = false;
    this.overlay = 'create';
    this.newGroupName = '';
    this.createError = '';
  }

  openJoinDialog(): void {
    this.isFabMenuOpen = false;
    this.overlay = 'join';
    this.joinGroupId = '';
    this.joinReferralCode = '';
    this.joinError = '';
  }

  closeOverlay(): void {
    this.overlay = 'none';
    this.createdGroup = null;
    this.copied = false;
    if (this.copiedTimeout) {
      clearTimeout(this.copiedTimeout);
      this.copiedTimeout = null;
    }
  }

  // ── Add Friend ──

  openAddFriendDialog(): void {
    this.isFabMenuOpen = false;
    this.overlay = 'add-friend';
    this.friendSearchQuery = '';
    this.friendSearchResults = [];
    this.friendSendSuccess = '';
    this.friendSendError = '';
  }

  onFriendSearchInput(): void {
    if (this.searchTimeout) clearTimeout(this.searchTimeout);

    const query = this.friendSearchQuery.trim();
    if (!query) {
      this.friendSearchResults = [];
      return;
    }

    this.friendSearchLoading = true;
    this.searchTimeout = setTimeout(() => {
      this.connectionService.searchUsers(query).subscribe({
        next: (results) => {
          this.friendSearchResults = results;
          this.friendSearchLoading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.friendSearchResults = [];
          this.friendSearchLoading = false;
          this.cdr.markForCheck();
        },
      });
    }, 300); // debounce 300ms
  }

  sendFriendRequest(username: string): void {
    this.friendSendError = '';
    this.friendSendSuccess = '';

    this.connectionService.sendRequest(username).subscribe({
      next: () => {
        this.friendSendSuccess = `Request sent to ${username}!`;
        // Remove user from search results
        this.friendSearchResults = this.friendSearchResults.filter(u => u.username !== username);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.friendSendError = err.error?.message || 'Failed to send request.';
        this.cdr.markForCheck();
      },
    });
  }

  /**
   * Returns live online status for a user.
   * Prefers the WebSocket presence map (real-time), falls back to the REST response value.
   */
  isUserOnline(user: UserResponse): boolean {
    return this.presenceMap.has(user.username)
      ? this.presenceMap.get(user.username)!
      : user.online;
  }

  // ── Pending Requests ──

  openPendingDialog(): void {
    this.isFabMenuOpen = false;
    this.overlay = 'pending';
    this.pendingLoading = true;

    this.connectionService.getPendingRequests().subscribe({
      next: (requests) => {
        this.pendingRequests = requests;
        this.pendingLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.pendingRequests = [];
        this.pendingLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  acceptRequest(requestId: string): void {
    this.connectionService.acceptRequest(requestId).subscribe({
      next: (response) => {
        // Remove from pending list
        this.pendingRequests = this.pendingRequests.filter(r => r.id !== requestId);
        this.pendingCount = Math.max(0, this.pendingCount - 1);
        this.cdr.markForCheck();
        this.closeOverlay();
        // Emit to parent to reload conversations and select the new chat
        if (response.conversationId) {
          this.connectionAccepted.emit(response.conversationId);
        }
      },
      error: (err) => {
        console.error('Failed to accept request:', err);
      },
    });
  }

  rejectRequest(requestId: string): void {
    this.connectionService.rejectRequest(requestId).subscribe({
      next: () => {
        this.pendingRequests = this.pendingRequests.filter(r => r.id !== requestId);
        this.pendingCount = Math.max(0, this.pendingCount - 1);
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to reject request:', err);
      },
    });
  }

  // ── Create Group ──

  submitCreateGroup(): void {
    const name = this.newGroupName.trim();
    if (!name) {
      this.createError = 'Group name is required.';
      return;
    }

    this.isCreating = true;
    this.createError = '';

    this.groupService.createGroup({ name }).subscribe({
      next: (group) => {
        this.createdGroup = group;
        this.overlay = 'created';
        this.isCreating = false;
        this.groupCreated.emit(group);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.createError = err.error?.message || 'Failed to create group.';
        this.isCreating = false;
        this.cdr.markForCheck();
      },
    });
  }

  // ── Join Group ──

  submitJoinGroup(): void {
    const id = this.joinGroupId.trim();
    const code = this.joinReferralCode.trim();
    if (!id || !code) {
      this.joinError = 'Group ID and referral code are both required.';
      return;
    }

    this.isJoining = true;
    this.joinError = '';

    this.groupService.joinGroup(id, { referralCode: code }).subscribe({
      next: (group) => {
        this.overlay = 'none';
        this.isJoining = false;
        this.groupJoined.emit(group);
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.joinError = err.error?.message || 'Failed to join group.';
        this.isJoining = false;
        this.cdr.markForCheck();
      },
    });
  }

  // ── Clipboard ──

  get inviteString(): string {
    if (!this.createdGroup) return '';
    return `Join "${this.createdGroup.name}" on Secure Chat!\nGroup ID: ${this.createdGroup.id}\nReferral Code: ${this.createdGroup.referralCode}`;
  }

  async copyInviteLink(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.inviteString);
      this.copied = true;
      this.cdr.markForCheck();

      if (this.copiedTimeout) clearTimeout(this.copiedTimeout);
      this.copiedTimeout = setTimeout(() => {
        this.copied = false;
        this.cdr.markForCheck();
      }, 2000);
    } catch {
      // Fallback for older browsers
      const textarea = document.createElement('textarea');
      textarea.value = this.inviteString;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      this.copied = true;
      this.cdr.markForCheck();

      if (this.copiedTimeout) clearTimeout(this.copiedTimeout);
      this.copiedTimeout = setTimeout(() => {
        this.copied = false;
        this.cdr.markForCheck();
      }, 2000);
    }
  }

  // ── Pinned Chats Persistence ──

  private loadPinnedChats(): Set<string> {
    try {
      const stored = localStorage.getItem(PINNED_CHATS_KEY);
      if (stored) {
        const ids = JSON.parse(stored) as string[];
        return new Set(ids);
      }
    } catch {
      // Corrupted data — reset
      localStorage.removeItem(PINNED_CHATS_KEY);
    }
    return new Set();
  }

  private savePinnedChats(): void {
    localStorage.setItem(PINNED_CHATS_KEY, JSON.stringify([...this.pinnedChatIds]));
  }

  // ── Settings Actions ──
  
  saveSettings(): void {
    this.settingsLoading = true;
    this.settingsError = '';
    this.settingsSuccess = '';

    this.userService.updateSettings({
      lastSeenPrivacy: this.lastSeenPrivacy,
      readReceiptsEnabled: this.readReceiptsEnabled
    }).subscribe({
      next: () => {
        this.settingsSuccess = 'Settings saved successfully';
        this.settingsLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.settingsError = 'Failed to save settings';
        this.settingsLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  confirmDeleteAccount(): void {
    this.overlay = 'delete-confirm';
  }

  deleteAccount(): void {
    this.settingsLoading = true;
    this.userService.deleteAccount().subscribe({
      next: () => {
        this.onLogout();
      },
      error: () => {
        this.settingsError = 'Failed to delete account';
        this.settingsLoading = false;
        this.overlay = 'settings';
        this.cdr.markForCheck();
      }
    });
  }
}
