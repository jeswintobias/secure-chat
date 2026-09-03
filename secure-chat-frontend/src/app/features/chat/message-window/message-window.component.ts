import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MessageResponse } from '../../../shared/models';
import { MessageBubbleComponent } from '../message-bubble/message-bubble.component';

@Component({
  selector: 'app-message-window',
  standalone: true,
  imports: [CommonModule, MessageBubbleComponent],
  templateUrl: './message-window.component.html',
  styleUrl: './message-window.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageWindowComponent implements AfterViewChecked {
  @Input() messages: MessageResponse[] = [];
  @Input() currentUsername = '';
  @Input() isAdmin = false;
  @Input() readReceiptsEnabled = true;

  @Output() pinMessage = new EventEmitter<string>();
  @Output() unpinMessage = new EventEmitter<string>();
  @Output() editMessage = new EventEmitter<{ messageId: string; content: string }>();
  @Output() deleteMessage = new EventEmitter<string>();
  @Output() deleteForMe = new EventEmitter<string>();
  @Output() reactToMessage = new EventEmitter<{ messageId: string; emoji: string }>();

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef<HTMLDivElement>;

  private shouldScroll = true;

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
    }
  }

  /** Determines if a date separator should be shown above a message. */
  showDateSeparator(index: number): boolean {
    if (index === 0) return true;
    const currentDate = this.messages[index].createdAt;
    const prevDate = this.messages[index - 1].createdAt;
    if (!currentDate || !prevDate) return false;
    const current = new Date(currentDate).toDateString();
    const prev = new Date(prevDate).toDateString();
    return current !== prev;
  }

  /** Formats a date for the separator label. */
  formatDateSeparator(dateStr: string): string {
    if (!dateStr) return 'Today';
    const date = new Date(dateStr);
    if (isNaN(date.getTime()) || date.getTime() === 0) return 'Today';
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === today.toDateString()) return 'Today';
    if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
    return date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
  }

  private scrollToBottom(): void {
    try {
      const el = this.scrollContainer?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    } catch { /* noop */ }
  }
}
