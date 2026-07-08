import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ConnectionRequestResponse, UserResponse } from '../../shared/models';

/**
 * Service for managing connection (friend) requests and user search.
 *
 * Handles sending, accepting, rejecting connection requests,
 * and partial username search for the "Add Friend" flow.
 */
@Injectable({ providedIn: 'root' })
export class ConnectionService {

  private readonly connectionsUrl = `${environment.apiUrl}/connections`;
  private readonly usersUrl = `${environment.apiUrl}/users`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Searches for users by partial username (prefix match).
   * Returns up to 10 results, excluding the current user.
   */
  searchUsers(query: string): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>(`${this.usersUrl}/search`, {
      params: { query },
    });
  }

  /**
   * Sends a connection request to the target user.
   */
  sendRequest(targetUsername: string): Observable<ConnectionRequestResponse> {
    return this.http.post<ConnectionRequestResponse>(`${this.connectionsUrl}/send`, {
      targetUsername,
    });
  }

  /**
   * Accepts a pending connection request.
   * Returns the updated request with the new conversation ID.
   */
  acceptRequest(requestId: string): Observable<ConnectionRequestResponse> {
    return this.http.post<ConnectionRequestResponse>(
      `${this.connectionsUrl}/${requestId}/accept`, {}
    );
  }

  /**
   * Rejects a pending connection request.
   */
  rejectRequest(requestId: string): Observable<ConnectionRequestResponse> {
    return this.http.post<ConnectionRequestResponse>(
      `${this.connectionsUrl}/${requestId}/reject`, {}
    );
  }

  /**
   * Returns all pending incoming connection requests.
   */
  getPendingRequests(): Observable<ConnectionRequestResponse[]> {
    return this.http.get<ConnectionRequestResponse[]>(`${this.connectionsUrl}/pending`);
  }

  /**
   * Returns all sent connection requests.
   */
  getSentRequests(): Observable<ConnectionRequestResponse[]> {
    return this.http.get<ConnectionRequestResponse[]>(`${this.connectionsUrl}/sent`);
  }
}
