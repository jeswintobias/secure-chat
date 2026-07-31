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
    let dateStr = this.message.createdAt;
    // Guard against null/undefined createdAt (e.g. freshly received WebSocket messages)
    if (!dateStr) {
      return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    }
    // If the backend sends an ISO string without a timezone (no Z or + or -),
    // append 'Z' to treat it as UTC. This allows the browser to properly 
    // localize the time to the user's timezone.
    if (!dateStr.endsWith('Z') && !dateStr.match(/[+-]\d{2}:\d{2}$/)) {
      dateStr += 'Z';
    }
    const date = new Date(dateStr);
    // If date is invalid (epoch 0 / NaN), fall back to current time
    if (isNaN(date.getTime()) || date.getTime() === 0) {
      return new Date().toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });
    }
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

  /** True when the message is an audio attachment. */
  get isAudio(): boolean {
    return this.message.messageType === 'AUDIO';
  }

  /** True when the message is a video attachment. */
  get isVideo(): boolean {
    return this.message.messageType === 'VIDEO';
  }

  /** True when at least one other user has read this message. */
  get isRead(): boolean {
    return this.message.readReceipts?.length > 0;
  }

  /** Extracts a display filename from the attachment URL. */
  get displayFileName(): string {
    if (this.message.originalName) {
      return this.message.originalName;
    }
    if (!this.message.attachmentUrl) return 'file';
    const segments = this.message.attachmentUrl.split('/');
    return segments[segments.length - 1] ?? 'file';
  }

  /** Returns a smart icon based on the file's MIME type. */
  get fileIcon(): string {
    const type = this.message.attachmentType || '';
    if (type.includes('pdf')) return '📄';
    if (type.includes('spreadsheet') || type.includes('excel') || type.includes('csv')) return '📊';
    if (type.includes('word') || type.includes('document')) return '📝';
    if (type.includes('zip') || type.includes('compressed')) return '🗜️';
    if (type.includes('text')) return 'txt';
    return '📁';
  }

  // Lightbox state
  showLightbox = false;

  openLightbox(): void {
    if (this.isImage && this.message.attachmentUrl) {
      this.showLightbox = true;
    }
  }

  closeLightbox(): void {
    this.showLightbox = false;
  }

  onPin(): void {
    this.pinMessage.emit(this.message.id);
  }

  onUnpin(): void {
    this.unpinMessage.emit(this.message.id);
  }
}
