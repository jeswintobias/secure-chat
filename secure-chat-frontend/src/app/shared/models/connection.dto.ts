/**
 * Immutable TypeScript interfaces mirroring the backend Connection DTOs.
 *
 * Backend source: com.securechat.dto.response.ConnectionRequestResponse
 */

/** Maps to: com.securechat.dto.response.ConnectionRequestResponse */
export interface ConnectionRequestResponse {
  readonly id: string;                    // UUID
  readonly senderId: string;              // UUID
  readonly senderUsername: string;
  readonly receiverId: string;            // UUID
  readonly receiverUsername: string;
  readonly status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  readonly conversationId: string | null; // UUID, set when accepted
  readonly createdAt: string;             // ISO-8601 Instant
  readonly updatedAt: string;             // ISO-8601 Instant
}

/** Request body for sending a connection request */
export interface SendConnectionRequest {
  readonly targetUsername: string;
}
