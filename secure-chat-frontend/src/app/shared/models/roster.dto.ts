/**
 * Immutable TypeScript interface mirroring the backend RosterUpdatePayload.
 *
 * Backend source: com.securechat.dto.websocket.RosterUpdatePayload
 *
 * Broadcast to /topic/conversation/{conversationId}/roster
 * whenever the member list of a group changes (join or removal).
 */

export type RosterEventType = 'JOIN' | 'LEAVE';

export interface RosterUpdatePayload {
  readonly conversationId: string;     // UUID
  readonly memberCount: number;
  readonly memberUsernames: readonly string[];
  readonly changedUsername: string;
  readonly eventType: RosterEventType;
}
