/**
 * Immutable TypeScript interface mirroring the backend UserResponse DTO.
 *
 * Backend source: com.securechat.dto.response.UserResponse
 * Note: Sensitive data (password_hash, email) is deliberately omitted by the backend.
 */
export interface UserResponse {
  readonly id: string;              // UUID
  readonly username: string;
  readonly role: 'USER' | 'ADMIN';
  readonly online: boolean;
  readonly createdAt: string;       // ISO-8601 Instant
}
