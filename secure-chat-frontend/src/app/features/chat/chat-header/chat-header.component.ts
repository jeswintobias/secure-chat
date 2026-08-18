import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GroupResponse, MessageResponse } from '../../../shared/models';

/**
 * Presentational header component for the active conversation.
 *
 * Displays conversation name, member count, typing indicators,
 * and a collapsible pinned messages banner.
 */
@Component({
  selector: 'app-chat-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './chat-header.component.html',
  styleUrl: './chat-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatHeaderComponent {
  @Input() conversation!: GroupResponse;
  @Input() typingUsers: string[] = [];
  @Input() pinnedMessages: MessageResponse[] = [];
  @Input() presenceMap = new Map<string, boolean>();
  @Input() lastSeenMap = new Map<string, string | null>();
  @Input() currentUsername = '';

  showPinnedBanner = false;

  get typingText(): string {
    if (this.typingUsers.length === 0) return '';
    if (this.typingUsers.length === 1) return `${this.typingUsers[0]} is typing`;
    if (this.typingUsers.length === 2) return `${this.typingUsers[0]} and ${this.typingUsers[1]} are typing`;
    return `${this.typingUsers[0]} and ${this.typingUsers.length - 1} others are typing`;
  }

  getInitials(name: string): string {
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2);
  }

  togglePinnedBanner(): void {
    this.showPinnedBanner = !this.showPinnedBanner;
  }

  /**
   * For private chats, returns the other user's status string:
   * "Online" or "Last seen at ..." or "" if unknown.
   */
  get privateChatStatus(): string {
    if (this.conversation.type !== 'PRIVATE') return '';
    
    // Find the other member
    const username = this.conversation.memberUsernames?.find(u => u !== this.currentUsername);
    if (!username) return '';
    
    // Check real-time presence map
    if (this.presenceMap.has(username) && this.presenceMap.get(username)) {
      return 'Online';
    }
    
    // Check real-time last seen map
    if (this.lastSeenMap.has(username)) {
      const ls = this.lastSeenMap.get(username);
      if (ls) return `Last seen ${this.formatLastSeen(ls)}`;
    }
    
    return '';
  }

  private formatLastSeen(dateStr: string): string {
    if (!dateStr.endsWith('Z') && !dateStr.match(/[+-]\d{2}:\d{2}$/)) {
      dateStr += 'Z';
    }
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return '';
    
    const now = new Date();
    const isToday = date.getDate() === now.getDate() && date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
    
    if (isToday) {
      return 'today at ' + date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });
    }
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) + ' at ' + date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true });
  }
}
