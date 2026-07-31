/**
 * Immutable TypeScript interfaces mirroring the backend Auth DTOs.
 *
 * Backend sources:
 *   - com.securechat.dto.request.LoginRequest
 *   - com.securechat.dto.request.RegisterRequest
 *   - com.securechat.dto.response.AuthResponse
 */

/** Maps to: com.securechat.dto.request.LoginRequest */
export interface LoginRequest {
  readonly username: string;
  readonly password: string;
}

/** Maps to: com.securechat.dto.request.RegisterRequest */
export interface RegisterRequest {
  readonly username: string;
  readonly email: string;
  readonly password: string;
  readonly confirmPassword: string;
}

/** Maps to: com.securechat.dto.response.AuthResponse */
export interface AuthResponse {
  readonly token: string;
  readonly tokenType: string;        // Always "Bearer"
  readonly username: string;
  readonly email: string;
  readonly role: 'USER' | 'ADMIN';
  readonly expiresIn: number;        // milliseconds (default: 86400000 = 24h)
}
