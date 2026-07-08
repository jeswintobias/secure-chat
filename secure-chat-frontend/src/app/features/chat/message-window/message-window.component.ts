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

  @Output() pinMessage = new EventEmitter<string>();
  @Output() unpinMessage = new EventEmitter<string>();

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
    const current = new Date(this.messages[index].createdAt).toDateString();
    const prev = new Date(this.messages[index - 1].createdAt).toDateString();
    return current !== prev;
  }

  /** Formats a date for the separator label. */
  formatDateSeparator(dateStr: string): string {
    const date = new Date(dateStr);
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
