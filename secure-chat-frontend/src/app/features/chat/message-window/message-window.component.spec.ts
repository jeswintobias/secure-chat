import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageWindowComponent } from './message-window.component';
import { MessageResponse } from '../../../shared/models';

/**
 * Tests for the MessageWindowComponent.
 *
 * Verifies component creation, date separator logic, and date formatting.
 */
describe('MessageWindowComponent', () => {
  let component: MessageWindowComponent;
  let fixture: ComponentFixture<MessageWindowComponent>;

  /** Creates a minimal MessageResponse stub for testing. */
  function createMessage(overrides: Partial<MessageResponse> = {}): MessageResponse {
    return {
      id: 'msg-' + Math.random().toString(36).substring(7),
      conversationId: 'conv-1',
      senderUsername: 'alice',
      senderId: 'user-1',
      content: 'Hello',
      messageType: 'TEXT',
      createdAt: new Date().toISOString(),
      expiresAt: null,
      attachmentUrl: null,
      attachmentType: null,
      originalName: null,
      pinned: false,
      pinnedBy: null,
      pinnedAt: null,
      readReceipts: [],
      ...overrides,
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MessageWindowComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MessageWindowComponent);
    component = fixture.componentInstance;
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('showDateSeparator should return true for the first message (index 0)', () => {
    component.messages = [createMessage()];
    fixture.detectChanges();

    expect(component.showDateSeparator(0)).toBeTrue();
  });

  it('formatDateSeparator should return "Today" for today\'s date', () => {
    const todayIso = new Date().toISOString();

    const result = component.formatDateSeparator(todayIso);

    expect(result).toBe('Today');
  });
});
