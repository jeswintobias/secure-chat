/**
 * Immutable TypeScript interfaces mirroring the backend Message DTOs.
 *
 * Backend sources:
 *   - com.securechat.dto.response.MessageResponse
 *   - com.securechat.dto.response.MessageReadDto
 *   - com.securechat.dto.response.ReactionSummary
 *   - com.securechat.dto.response.ReactionResponse
 *   - com.securechat.dto.websocket.WebSocketMessagePayload
 *   - com.securechat.dto.websocket.MessageReadPayload
 *   - com.securechat.dto.websocket.MessageEditPayload
 *   - com.securechat.dto.websocket.MessageDeletePayload
 *   - com.securechat.dto.websocket.ReactionPayload
 *   - FileUploadController response Map<String, String>
 */

/** PostgreSQL ENUM: message_type */
export type MessageType = 'TEXT' | 'SYSTEM' | 'IMAGE' | 'FILE' | 'AUDIO' | 'VIDEO';

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
 * Maps to: com.securechat.dto.response.ReactionSummary
 *
 * Aggregated emoji reaction on a message — groups all users
 * who reacted with the same emoji.
 */
export interface ReactionSummary {
  readonly emoji: string;
  readonly count: number;
  readonly usernames: readonly string[];
}

/**
 * Maps to: com.securechat.dto.response.ReactionResponse
 *
 * Broadcast when a user adds or removes a reaction.
 */
export interface ReactionResponse {
  readonly messageId: string;          // UUID
  readonly username: string;
  readonly userId: string;             // UUID
  readonly emoji: string;
  readonly action: 'ADDED' | 'REMOVED';
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

  /** Original filename of the attachment (human-readable). */
  readonly originalName: string | null;

  /** Whether this message is pinned in its conversation. */
  readonly pinned: boolean;

  /** Username of the admin who pinned this message. */
  readonly pinnedBy: string | null;

  /** Timestamp when this message was pinned. */
  readonly pinnedAt: string | null;        // ISO-8601 Instant

  /** Whether this message's content is client-side encrypted (E2EE). */
  readonly encrypted: boolean;

  /** Base64-encoded AES-GCM initialization vector. Null for non-encrypted messages. */
  readonly iv: string | null;

  /** List of read receipts for this message. */
  readonly readReceipts: readonly MessageReadDto[];

  /** Whether this message has been edited after sending. */
  readonly edited: boolean;

  /** Timestamp of the most recent edit. Null if never edited. */
  readonly editedAt: string | null;        // ISO-8601 Instant

  /** Whether this message has been deleted for everyone (soft delete). */
  readonly deleted: boolean;

  /** Timestamp of deletion. Null if not deleted. */
  readonly deletedAt: string | null;       // ISO-8601 Instant

  /** Username of the user who deleted this message. */
  readonly deletedBy: string | null;

  /** Aggregated emoji reactions for this message. */
  readonly reactions: readonly ReactionSummary[];
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

  /** Original filename of the attachment. */
  readonly originalName?: string | null;

  /** Whether the content is client-side encrypted (E2EE). */
  readonly encrypted?: boolean;

  /** Base64-encoded AES-GCM initialization vector. Required when encrypted=true. */
  readonly iv?: string | null;
}

/**
 * Maps to: com.securechat.dto.websocket.MessageEditPayload
 *
 * Sent by clients to /app/chat.edit/{conversationId} to edit a message.
 */
export interface MessageEditPayload {
  readonly messageId: string;              // UUID
  readonly content: string;
  readonly encrypted?: boolean;
  readonly iv?: string | null;
}

/**
 * Maps to: com.securechat.dto.websocket.MessageDeletePayload
 *
 * Sent by clients to /app/chat.delete/{conversationId} to delete a message.
 */
export interface MessageDeletePayload {
  readonly messageId: string;              // UUID
  readonly mode: 'EVERYONE' | 'ME';
}

/**
 * Maps to: com.securechat.dto.websocket.ReactionPayload
 *
 * Sent by clients to /app/chat.react/{conversationId} to toggle a reaction.
 */
export interface ReactionPayload {
  readonly messageId: string;              // UUID
  readonly emoji: string;
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

