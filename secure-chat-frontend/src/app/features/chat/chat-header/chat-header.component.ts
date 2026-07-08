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
}
