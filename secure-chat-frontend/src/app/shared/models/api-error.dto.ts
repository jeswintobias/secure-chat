/**
 * Immutable TypeScript interface mirroring the backend ApiErrorResponse DTO.
 *
 * Backend source: com.securechat.dto.response.ApiErrorResponse
 *
 * All API errors (400, 401, 403, 404, 500) return this standardized shape.
 * The fieldErrors map is only populated for validation errors (400).
 */
export interface ApiErrorResponse {
  readonly status: number;
  readonly error: string;
  readonly message: string;
  readonly path: string;
  readonly timestamp: string;                               // ISO-8601 Instant
  readonly fieldErrors?: Readonly<Record<string, string>>;  // field -> validation message
}
