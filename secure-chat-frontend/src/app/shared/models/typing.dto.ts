/**
 * Immutable TypeScript interface mirroring the backend TypingIndicatorPayload.
 *
 * Backend source: com.securechat.dto.websocket.TypingIndicatorPayload
 *
 * This is a fire-and-forget event — no persistence needed.
 * Broadcast to /topic/conversation/{conversationId}/typing
 */
export interface TypingIndicatorPayload {
  readonly conversationId: string;   // UUID
  readonly username: string;
  readonly typing: boolean;
}
