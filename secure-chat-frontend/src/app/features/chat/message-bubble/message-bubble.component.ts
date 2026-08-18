import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageResponse, ReactionSummary } from '../../../shared/models';

/**
 * Presentational component for a single chat message bubble.
 *
 * Responsibilities:
 * - Renders text content, image attachments, and file attachments
 * - Shows read receipt ticks for own messages (single = sent, double = read)
 * - Displays ephemeral (⏳) and pinned (📌) indicators
 * - Exposes pin/unpin actions for admin users via hover menu
 * - **Edit mode**: Inline editing with save/cancel for own messages
 * - **Delete**: Soft delete for own messages ("This message was deleted")
 * - **Reactions**: Quick emoji reaction bar + reaction summary display
 */
@Component({
  selector: 'app-message-bubble',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './message-bubble.component.html',
  styleUrl: './message-bubble.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageBubbleComponent {
  @Input() message!: MessageResponse;
  @Input() isOwn = false;
  @Input() isAdmin = false;
  @Input() readReceiptsEnabled = true;
  @Input() currentUsername = '';

  @Output() pinMessage = new EventEmitter<string>();
  @Output() unpinMessage = new EventEmitter<string>();
  @Output() editMessage = new EventEmitter<{ messageId: string; content: string }>();
  @Output() deleteMessage = new EventEmitter<string>();
  @Output() reactToMessage = new EventEmitter<{ messageId: string; emoji: string }>();

  /** Predefined quick-reaction emojis. */
  readonly quickReactions = ['👍', '❤️', '😂', '😮', '😢', '🙏'];

  /** Whether the reaction picker bar is visible. */
  showReactionBar = false;

  /** Whether the message is in edit mode. */
  isEditing = false;

  /** Temporary edit content bound to the input. */
  editContent = '';

  /** Whether the hover action menu is visible. */
  showActions = false;

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
    if (!this.readReceiptsEnabled) return false;
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

  /** Checks if the current user has reacted with a given emoji. */
  hasReacted(reaction: ReactionSummary): boolean {
    return reaction.usernames?.includes(this.currentUsername) ?? false;
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

  // ════════════════════════════════════════════════════════════
  // Edit Mode
  // ════════════════════════════════════════════════════════════

  enterEditMode(): void {
    this.isEditing = true;
    this.editContent = this.message.content;
    this.showActions = false;
  }

  cancelEdit(): void {
    this.isEditing = false;
    this.editContent = '';
  }

  saveEdit(): void {
    const trimmed = this.editContent.trim();
    if (!trimmed || trimmed === this.message.content) {
      this.cancelEdit();
      return;
    }
    this.editMessage.emit({ messageId: this.message.id, content: trimmed });
    this.isEditing = false;
    this.editContent = '';
  }

  onEditKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.saveEdit();
    } else if (event.key === 'Escape') {
      this.cancelEdit();
    }
  }

  // ════════════════════════════════════════════════════════════
  // Delete
  // ════════════════════════════════════════════════════════════

  onDelete(): void {
    this.deleteMessage.emit(this.message.id);
    this.showActions = false;
  }

  // ════════════════════════════════════════════════════════════
  // Reactions
  // ════════════════════════════════════════════════════════════

  toggleReactionBar(): void {
    this.showReactionBar = !this.showReactionBar;
  }

  onReact(emoji: string): void {
    this.reactToMessage.emit({ messageId: this.message.id, emoji });
    this.showReactionBar = false;
  }
}
