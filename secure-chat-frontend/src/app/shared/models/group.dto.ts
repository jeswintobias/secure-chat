/**
 * Immutable TypeScript interfaces mirroring the backend Group DTOs.
 *
 * Backend sources:
 *   - com.securechat.dto.response.GroupResponse
 *   - com.securechat.dto.request.CreateGroupRequest
 *   - com.securechat.dto.request.JoinGroupRequest
 */

/** Maps to: com.securechat.dto.response.GroupResponse */
export interface GroupResponse {
  readonly id: string;                            // UUID
  readonly name: string;
  readonly type: 'GROUP' | 'PRIVATE';             // Conversation type
  readonly referralCode: string;
  readonly memberCount: number;
  readonly memberUsernames: readonly string[];
  readonly createdAt: string;                     // ISO-8601 Instant
  readonly updatedAt: string;                     // ISO-8601 Instant
}

/** Maps to: com.securechat.dto.request.CreateGroupRequest */
export interface CreateGroupRequest {
  readonly name: string;
  readonly referralCode?: string;                 // optional — auto-generated if omitted
}

/** Maps to: com.securechat.dto.request.JoinGroupRequest */
export interface JoinGroupRequest {
  readonly referralCode: string;
}
