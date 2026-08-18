import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserResponse } from '../../shared/models/user.dto';

/**
 * REST service for user profile operations.
 *
 * Backend endpoints: /api/users/**
 * Controller: UserController
 */
@Injectable({ providedIn: 'root' })
export class UserService {

  private readonly baseUrl = `${environment.apiUrl}/users`;

  constructor(private readonly http: HttpClient) {}

  /** GET /api/users/me — Returns the authenticated user's profile. */
  getCurrentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/me`);
  }

  /** GET /api/users/{userId} — Returns a user's public profile. */
  getUserById(userId: string): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.baseUrl}/${userId}`);
  }

  /** PATCH /api/users/me/settings — Updates user privacy settings. */
  updateSettings(settings: { lastSeenPrivacy?: string, readReceiptsEnabled?: boolean }): Observable<UserResponse> {
    return this.http.patch<UserResponse>(`${this.baseUrl}/me/settings`, settings);
  }

  /** DELETE /api/users/me — Soft deletes the current user's account. */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/me`);
  }

  /**
   * GET /api/users/presence — Returns online statuses of all users
   * in the current user's conversations.
   *
   * Called once on startup to seed the presenceMap so that users
   * who were already online appear with the correct status.
   */
  getPresenceStatuses(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(`${this.baseUrl}/presence`);
  }
}
