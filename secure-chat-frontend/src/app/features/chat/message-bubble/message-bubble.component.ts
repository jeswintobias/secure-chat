import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MessageResponse } from '../../../shared/models';

/**
 * Presentational component for a single chat message bubble.
 *
 * Responsibilities:
 * - Renders text content, image attachments, and file attachments
 * - Shows read receipt ticks for own messages (single = sent, double = read)
 * - Displays ephemeral (⏳) and pinned (📌) indicators
 * - Exposes pin/unpin actions for admin users via hover menu
 */
@Component({
  selector: 'app-message-bubble',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './message-bubble.component.html',
  styleUrl: './message-bubble.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageBubbleComponent {
  @Input() message!: MessageResponse;
  @Input() isOwn = false;
  @Input() isAdmin = false;

  @Output() pinMessage = new EventEmitter<string>();
  @Output() unpinMessage = new EventEmitter<string>();

  get formattedTime(): string {
    const date = new Date(this.message.createdAt);
    return date.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
  }

  get isEphemeral(): boolean {
    return !!this.message.expiresAt;
  }

  /** True when the message has an image attachment. */
  get isImage(): boolean {
    return this.message.messageType === 'IMAGE';
  }

  /** True when the message has a non-image file attachment. */
  get isFile(): boolean {
    return this.message.messageType === 'FILE';
  }

  /** True when at least one other user has read this message. */
  get isRead(): boolean {
    return this.message.readReceipts?.length > 0;
  }

  /** Extracts a display filename from the attachment URL. */
  get fileName(): string {
    if (!this.message.attachmentUrl) return 'file';
    const segments = this.message.attachmentUrl.split('/');
    return segments[segments.length - 1] ?? 'file';
  }

  onPin(): void {
    this.pinMessage.emit(this.message.id);
  }

  onUnpin(): void {
    this.unpinMessage.emit(this.message.id);
  }
}
