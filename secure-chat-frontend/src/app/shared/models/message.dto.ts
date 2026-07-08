/**
 * Immutable TypeScript interfaces mirroring the backend Message DTOs.
 *
 * Backend sources:
 *   - com.securechat.dto.response.MessageResponse
 *   - com.securechat.dto.response.MessageReadDto
 *   - com.securechat.dto.websocket.WebSocketMessagePayload
 *   - com.securechat.dto.websocket.MessageReadPayload
 *   - FileUploadController response Map<String, String>
 */

/** PostgreSQL ENUM: message_type */
export type MessageType = 'TEXT' | 'SYSTEM' | 'IMAGE' | 'FILE';

/**
 * Maps to: com.securechat.dto.response.MessageReadDto
 *
 * Represents a single read receipt — one user having read one message.
 */
export interface MessageReadDto {
  readonly userId: string;             // UUID
  readonly username: string;
  readonly readAt: string;             // ISO-8601 Instant
}

/**
 * Maps to: com.securechat.dto.response.MessageResponse
 *
 * Used in both REST responses (GET /api/conversations/{id}/messages)
 * and WebSocket broadcasts (/topic/conversation/{id}).
 */
export interface MessageResponse {
  readonly id: string;                     // UUID
  readonly conversationId: string;         // UUID
  readonly senderUsername: string;
  readonly senderId: string;               // UUID
  readonly content: string;                // XSS-sanitized on server
  readonly messageType: MessageType;
  readonly createdAt: string;              // ISO-8601 Instant
  readonly expiresAt: string | null;       // null = non-ephemeral

  /** URL/path to uploaded file attachment. Null for text-only messages. */
  readonly attachmentUrl: string | null;

  /** MIME type of the attachment (e.g. image/png, application/pdf). */
  readonly attachmentType: string | null;

  /** Whether this message is pinned in its conversation. */
  readonly pinned: boolean;

  /** Username of the admin who pinned this message. */
  readonly pinnedBy: string | null;

  /** Timestamp when this message was pinned. */
  readonly pinnedAt: string | null;        // ISO-8601 Instant

  /** List of read receipts for this message. */
  readonly readReceipts: readonly MessageReadDto[];
}

/**
 * Maps to: com.securechat.dto.websocket.WebSocketMessagePayload
 *
 * Sent by the client to /app/chat.send/{conversationId}.
 * The conversationId is part of the STOMP destination path, not this payload.
 */
export interface WebSocketMessagePayload {
  readonly content: string;
  readonly expiryMinutes?: number | null;  // 0 or null = non-ephemeral

  /** URL of an uploaded attachment (set after file upload via REST). */
  readonly attachmentUrl?: string | null;

  /** MIME type of the attachment. */
  readonly attachmentType?: string | null;
}

/**
 * Maps to: com.securechat.dto.websocket.MessageReadPayload
 *
 * Sent by clients to /app/chat.read/{conversationId} to indicate
 * they have read a message, and broadcast by the server on
 * /topic/conversation/{conversationId}/read.
 */
export interface MessageReadPayload {
  readonly messageId: string;              // UUID
  readonly conversationId: string;         // UUID
  readonly userId?: string;                // UUID — set by server
  readonly username?: string;              // set by server
  readonly readAt?: string;                // ISO-8601 — set by server
}

/**
 * Response from POST /api/upload (FileUploadController).
 * The backend returns a Map<String, String> with these keys.
 */
export interface FileUploadResponse {
  readonly url: string;
  readonly contentType: string;
  readonly originalName: string;
}

/**
 * Spring Data Page wrapper for paginated REST responses.
 * Maps to: org.springframework.data.domain.Page<T>
 */
export interface Page<T> {
  readonly content: readonly T[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;                 // current page (0-indexed)
  readonly size: number;
  readonly first: boolean;
  readonly last: boolean;
  readonly empty: boolean;
}
