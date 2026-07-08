/**
 * WebSocket error payload received from /user/queue/errors.
 *
 * Sent by the backend WebSocketExceptionHandler when a STOMP
 * @MessageMapping handler throws an exception (e.g., blocked URL,
 * resource not found, authorization failure).
 */
export interface WebSocketErrorPayload {
  errorType: 'UNSAFE_URL' | 'NOT_FOUND' | 'BAD_REQUEST' | 'FORBIDDEN' | 'INTERNAL_ERROR';
  message: string;
  details?: BlockedUrlDetail[];
  timestamp: string;
}

/**
 * Detail about a single URL blocked by the URL security pipeline.
 * Only present when errorType is 'UNSAFE_URL'.
 */
export interface BlockedUrlDetail {
  url: string;
  reason: string;
}
