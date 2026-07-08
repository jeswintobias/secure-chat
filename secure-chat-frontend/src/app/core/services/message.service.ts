import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MessageResponse, Page } from '../../shared/models/message.dto';

/**
 * REST service for message operations.
 *
 * Backend endpoints:
 *   GET  /api/conversations/{conversationId}/messages        — paginated history
 *   GET  /api/conversations/{conversationId}/messages/pinned — pinned messages
 *   POST /api/messages/{messageId}/pin                       — pin a message (ADMIN)
 *   POST /api/messages/{messageId}/unpin                     — unpin a message (ADMIN)
 *
 * Controller: ChatController
 */
@Injectable({ providedIn: 'root' })
export class MessageService {

  private readonly baseUrl = `${environment.apiUrl}/conversations`;
  private readonly messagesUrl = `${environment.apiUrl}/messages`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Retrieves paginated message history for a conversation.
   * Messages are ordered newest-first and exclude expired ephemeral messages.
   *
   * @param conversationId UUID of the conversation
   * @param page zero-based page index (default: 0)
   * @param size page size (default: 50)
   */
  getHistory(
    conversationId: string,
    page = 0,
    size = 50,
  ): Observable<Page<MessageResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<Page<MessageResponse>>(
      `${this.baseUrl}/${conversationId}/messages`,
      { params },
    );
  }

  /**
   * Retrieves all pinned messages for a conversation.
   *
   * @param conversationId UUID of the conversation
   */
  getPinnedMessages(conversationId: string): Observable<MessageResponse[]> {
    return this.http.get<MessageResponse[]>(
      `${this.baseUrl}/${conversationId}/messages/pinned`,
    );
  }

  /**
   * Pins a message. Only users with ADMIN role can pin messages.
   *
   * @param messageId UUID of the message to pin
   */
  pinMessage(messageId: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(
      `${this.messagesUrl}/${messageId}/pin`,
      {},
    );
  }

  /**
   * Unpins a message. Only users with ADMIN role can unpin messages.
   *
   * @param messageId UUID of the message to unpin
   */
  unpinMessage(messageId: string): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(
      `${this.messagesUrl}/${messageId}/unpin`,
      {},
    );
  }
}
