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
}
