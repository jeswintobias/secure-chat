/**
 * Payload broadcast by the backend when a user's online status changes.
 *
 * Backend source: com.securechat.dto.websocket.PresencePayload
 * STOMP topic: /topic/presence
 */
export interface PresencePayload {
  readonly username: string;
  readonly online: boolean;
  readonly lastSeen?: string;
}
