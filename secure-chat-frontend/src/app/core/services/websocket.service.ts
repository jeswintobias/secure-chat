import { Injectable, OnDestroy } from '@angular/core';
import { RxStomp, RxStompConfig } from '@stomp/rx-stomp';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { MessageResponse } from '../../shared/models/message.dto';
import { TypingIndicatorPayload } from '../../shared/models/typing.dto';
import { WebSocketMessagePayload, MessageReadPayload } from '../../shared/models/message.dto';
import { RosterUpdatePayload } from '../../shared/models/roster.dto';
import { WebSocketErrorPayload } from '../../shared/models/websocket-error.dto';

/**
 * Production-grade reactive WebSocket service wrapping @stomp/rx-stomp.
 *
 * Architecture:
 * - Uses SockJS transport (matches backend's .withSockJS() config)
 * - Injects fresh JWT into STOMP CONNECT headers via beforeConnect hook
 * - Exposes conversation messages and typing indicators as Observable streams
 * - Automatic reconnection with 5-second delay
 * - Heartbeat keep-alive (10s incoming, 10s outgoing)
 *
 * Backend reference:
 *   WebSocketConfig.java — endpoint /ws with SockJS, prefixes /app, /topic, /queue
 *   WebSocketAuthInterceptor.java — validates JWT from STOMP CONNECT headers
 */
@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {

  private readonly rxStomp: RxStomp;

  constructor(private readonly authService: AuthService) {
    this.rxStomp = new RxStomp();
  }

  /**
   * Observable that emits when the STOMP connection is (re)established.
   * Consumers can use this to re-sync state after a reconnection.
   */
  get connected$(): Observable<void> {
    return this.rxStomp.connected$.pipe(map(() => void 0));
  }

  /**
   * Activates the STOMP connection.
   * Must be called after successful login when a JWT is available.
   */
  connect(): void {
    const config: RxStompConfig = {
      // SockJS transport URL — matches backend's /ws endpoint with SockJS fallback
      brokerURL: `${environment.wsUrl}/websocket`,

      // Dynamic JWT injection on every (re)connection attempt
      beforeConnect: (client) => {
        const token = this.authService.getToken();
        if (token) {
          client.configure({
            connectHeaders: {
              Authorization: `Bearer ${token}`,
            },
          });
        }
      },

      // Reconnection strategy
      reconnectDelay: 5000,

      // Heartbeat keep-alive (matches server expectations)
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,

      // Disable debug logging in production
      debug: (msg: string) => {
        if (!environment.production) {
          console.debug('[STOMP]', msg);
        }
      },
    };

    this.rxStomp.configure(config);
    this.rxStomp.activate();
  }

  /**
   * Cleanly deactivates the STOMP connection.
   * Called on logout or component destruction.
   */
  disconnect(): void {
    if (this.rxStomp.active) {
      this.rxStomp.deactivate();
    }
  }

  /**
   * Subscribes to real-time messages for a specific conversation.
   * Returns an Observable that emits MessageResponse objects as they arrive.
   *
   * STOMP destination: /topic/conversation/{conversationId}
   */
  watchConversation(conversationId: string): Observable<MessageResponse> {
    return this.rxStomp
      .watch(`/topic/conversation/${conversationId}`)
      .pipe(map(message => JSON.parse(message.body) as MessageResponse));
  }

  /**
   * Subscribes to typing indicator events for a specific conversation.
   *
   * STOMP destination: /topic/conversation/{conversationId}/typing
   */
  watchTyping(conversationId: string): Observable<TypingIndicatorPayload> {
    return this.rxStomp
      .watch(`/topic/conversation/${conversationId}/typing`)
      .pipe(map(message => JSON.parse(message.body) as TypingIndicatorPayload));
  }

  /**
   * Subscribes to roster update events for a specific conversation.
   * Emits when members join or leave the group.
   *
   * STOMP destination: /topic/conversation/{conversationId}/roster
   */
  watchRoster(conversationId: string): Observable<RosterUpdatePayload> {
    return this.rxStomp
      .watch(`/topic/conversation/${conversationId}/roster`)
      .pipe(map(message => JSON.parse(message.body) as RosterUpdatePayload));
  }

  /**
   * Sends a chat message to a conversation via STOMP.
   *
   * STOMP destination: /app/chat.send/{conversationId}
   * The server-side ChatController.sendMessage() handles sanitization,
   * persistence, and broadcast.
   */
  sendMessage(conversationId: string, payload: WebSocketMessagePayload): void {
    this.rxStomp.publish({
      destination: `/app/chat.send/${conversationId}`,
      body: JSON.stringify(payload),
    });
  }

  /**
   * Sends a typing indicator event.
   *
   * STOMP destination: /app/chat.typing/{conversationId}
   * The server overrides the username with the authenticated principal
   * to prevent spoofing.
   */
  sendTyping(conversationId: string, typing: boolean): void {
    const payload: TypingIndicatorPayload = {
      conversationId,
      username: this.authService.getCurrentUsername(),
      typing,
    };
    this.rxStomp.publish({
      destination: `/app/chat.typing/${conversationId}`,
      body: JSON.stringify(payload),
    });
  }

  /**
   * Subscribes to read receipt events for a specific conversation.
   *
   * STOMP destination: /topic/conversation/{conversationId}/read
   */
  watchReadReceipts(conversationId: string): Observable<MessageReadPayload> {
    return this.rxStomp
      .watch(`/topic/conversation/${conversationId}/read`)
      .pipe(map(message => JSON.parse(message.body) as MessageReadPayload));
  }

  /**
   * Sends a read receipt for a message.
   *
   * STOMP destination: /app/chat.read/{conversationId}
   * The server overrides userId/username with the authenticated principal.
   */
  sendReadReceipt(conversationId: string, messageId: string): void {
    const payload: MessageReadPayload = {
      messageId,
      conversationId,
    };
    this.rxStomp.publish({
      destination: `/app/chat.read/${conversationId}`,
      body: JSON.stringify(payload),
    });
  }

  /**
   * Subscribes to WebSocket error events sent to the current user.
   *
   * STOMP destination: /user/queue/errors
   *
   * The backend {@code WebSocketExceptionHandler} sends errors here when
   * a @MessageMapping handler throws an exception (e.g., blocked URL,
   * resource not found, authorization failure).
   */
  watchErrors(): Observable<WebSocketErrorPayload> {
    return this.rxStomp
      .watch('/user/queue/errors')
      .pipe(map(message => JSON.parse(message.body) as WebSocketErrorPayload));
  }

  ngOnDestroy(): void {
    this.disconnect();
  }
}
