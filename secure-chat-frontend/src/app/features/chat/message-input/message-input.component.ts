import { Component, Output, EventEmitter, ChangeDetectionStrategy, ChangeDetectorRef, ViewChild, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

/**
 * Payload emitted by the message-input component when the user sends a message.
 * Includes the text content plus optional attachment and expiry metadata.
 */
export interface MessageInputPayload {
  content: string;
  attachmentFile?: File;
  expiryMinutes?: number;
}

/**
 * Expiry option for the ephemeral message dropdown.
 */
export interface ExpiryOption {
  label: string;
  value: number;  // minutes (−1 = custom, 0 = off)
}

@Component({
  selector: 'app-message-input',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './message-input.component.html',
  styleUrl: './message-input.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MessageInputComponent {
  @Output() messageSent = new EventEmitter<MessageInputPayload>();
  @Output() typingChanged = new EventEmitter<boolean>();

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  messageText = '';
  selectedFile: File | null = null;
  filePreviewUrl: string | null = null;
  isFileImage = false;
  isFileVideo = false;

  /** Ephemeral message expiry */
  showExpiryMenu = false;
  selectedExpiry: number | null = null;
  customExpiryMinutes: number | null = null;
  showCustomInput = false;

  readonly expiryOptions: ExpiryOption[] = [
    { label: 'Off', value: 0 },
    { label: '5 minutes', value: 5 },
    { label: '1 hour', value: 60 },
    { label: '1 day', value: 1440 },
    { label: 'Custom…', value: -1 },
  ];

  /** Max file size in bytes (25 MB to match backend). */
  private readonly MAX_FILE_SIZE = 25 * 1024 * 1024;
  fileSizeError = '';

  private typingSubject = new Subject<boolean>();
  private typingTimeout: ReturnType<typeof setTimeout> | null = null;

  constructor(private readonly cdr: ChangeDetectorRef, private readonly elRef: ElementRef) {
    this.typingSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
    ).subscribe(isTyping => this.typingChanged.emit(isTyping));
  }

  /** Close expiry dropdown when clicking outside of it. */
  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.showExpiryMenu) {
      const expiryWrapper = this.elRef.nativeElement.querySelector('.expiry-wrapper');
      if (expiryWrapper && !expiryWrapper.contains(event.target as Node)) {
        this.showExpiryMenu = false;
        this.showCustomInput = false;
        this.cdr.markForCheck();
      }
    }
  }

  // ════════════════════════════════════════════════════════════
  // Typing indicator logic (unchanged from original)
  // ════════════════════════════════════════════════════════════

  onInput(): void {
    this.typingSubject.next(true);

    // Auto-stop typing after 2 seconds of inactivity
    if (this.typingTimeout) clearTimeout(this.typingTimeout);
    this.typingTimeout = setTimeout(() => {
      this.typingSubject.next(false);
    }, 2000);
  }

  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  // ════════════════════════════════════════════════════════════
  // File Attachment
  // ════════════════════════════════════════════════════════════

  /** Opens the native file picker dialog. */
  openFilePicker(): void {
    this.fileInput.nativeElement.click();
  }

  /** Handles file selection from the input element. */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    // Enforce 10 MB limit client-side
    if (file.size > this.MAX_FILE_SIZE) {
      this.fileSizeError = `File exceeds 10 MB limit (${(file.size / (1024 * 1024)).toFixed(1)} MB)`;
      this.cdr.markForCheck();
      setTimeout(() => {
        this.fileSizeError = '';
        this.cdr.markForCheck();
      }, 4000);
      // Reset the input so the same file can be re-selected
      input.value = '';
      return;
    }

    this.fileSizeError = '';
    this.selectedFile = file;
    this.isFileImage = file.type.startsWith('image/');
    this.isFileVideo = file.type.startsWith('video/');

    // Generate preview URL for images and videos
    if (this.isFileImage || this.isFileVideo) {
      this.filePreviewUrl = URL.createObjectURL(file);
    } else {
      this.filePreviewUrl = null;
    }

    // Reset the input so the same file can be re-selected after removal
    input.value = '';
    this.cdr.markForCheck();
  }

  /** Removes the currently selected file attachment. */
  removeFile(): void {
    if (this.filePreviewUrl) {
      URL.revokeObjectURL(this.filePreviewUrl);
    }
    this.selectedFile = null;
    this.filePreviewUrl = null;
    this.isFileImage = false;
    this.isFileVideo = false;
    this.cdr.markForCheck();
  }

  // ════════════════════════════════════════════════════════════
  // Expiry Menu
  // ════════════════════════════════════════════════════════════

  toggleExpiryMenu(): void {
    this.showExpiryMenu = !this.showExpiryMenu;
    if (!this.showExpiryMenu) {
      this.showCustomInput = false;
    }
    this.cdr.markForCheck();
  }

  selectExpiry(option: ExpiryOption): void {
    if (option.value === -1) {
      // Show custom input
      this.showCustomInput = true;
      this.cdr.markForCheck();
      return;
    }
    this.selectedExpiry = option.value === 0 ? null : option.value;
    this.showExpiryMenu = false;
    this.showCustomInput = false;
    this.cdr.markForCheck();
  }

  applyCustomExpiry(): void {
    if (this.customExpiryMinutes && this.customExpiryMinutes > 0) {
      this.selectedExpiry = this.customExpiryMinutes;
    }
    this.showExpiryMenu = false;
    this.showCustomInput = false;
    this.cdr.markForCheck();
  }

  clearExpiry(): void {
    this.selectedExpiry = null;
    this.customExpiryMinutes = null;
    this.showExpiryMenu = false;
    this.showCustomInput = false;
    this.cdr.markForCheck();
  }

  get expiryLabel(): string {
    if (!this.selectedExpiry) return '';
    if (this.selectedExpiry < 60) return `${this.selectedExpiry}m`;
    if (this.selectedExpiry < 1440) return `${this.selectedExpiry / 60}h`;
    return `${this.selectedExpiry / 1440}d`;
  }

  // ════════════════════════════════════════════════════════════
  // Send
  // ════════════════════════════════════════════════════════════

  /** True when the user can send (has text or an attachment). */
  get canSend(): boolean {
    return this.messageText.trim().length > 0 || !!this.selectedFile;
  }

  send(): void {
    const text = this.messageText.trim();
    if (!text && !this.selectedFile) return;

    const payload: MessageInputPayload = {
      content: text,
    };

    if (this.selectedFile) {
      payload.attachmentFile = this.selectedFile;
    }

    if (this.selectedExpiry) {
      payload.expiryMinutes = this.selectedExpiry;
    }

    this.messageSent.emit(payload);

    // Reset state
    this.messageText = '';
    if (this.filePreviewUrl) {
      URL.revokeObjectURL(this.filePreviewUrl);
    }
    this.selectedFile = null;
    this.filePreviewUrl = null;
    this.isFileImage = false;
    this.isFileVideo = false;
    this.typingSubject.next(false);
    if (this.typingTimeout) clearTimeout(this.typingTimeout);
  }
}
