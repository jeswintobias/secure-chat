import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, RegisterRequest } from '../../shared/models';

const TOKEN_KEY = 'securechat_jwt';
const USER_KEY = 'securechat_user';
const EMAIL_KEY = 'securechat_email';
const ROLE_KEY = 'securechat_role';

/**
 * Core authentication service — singleton, provided at root.
 *
 * Manages JWT lifecycle: login, register, token storage, logout.
 * The JWT is stored in localStorage and injected into STOMP CONNECT
 * headers by WebSocketService and into HTTP requests by AuthInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly authUrl = `${environment.apiUrl}/auth`;

  /** Emits the current username (or null when logged out). */
  private readonly currentUserSubject = new BehaviorSubject<string | null>(
    this.getStoredUsername()
  );
  readonly currentUser$ = this.currentUserSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  /**
   * POST /api/auth/login
   * On success, stores the JWT and username, then emits to subscribers.
   */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/login`, request).pipe(
      tap(response => this.handleAuthSuccess(response))
    );
  }

  /**
   * POST /api/auth/register
   * On success, auto-logs in by storing the returned JWT.
   */
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authUrl}/register`, request).pipe(
      tap(response => this.handleAuthSuccess(response))
    );
  }

  /**
   * Clears all stored credentials and navigates to login.
   */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(ROLE_KEY);
    this.currentUserSubject.next(null);
    this.router.navigate(['/auth/login']);
  }

  /** Returns the stored JWT or null. */
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  /** Returns true if a token exists. */
  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  /** Returns the stored username or empty string. */
  getCurrentUsername(): string {
    return localStorage.getItem(USER_KEY) ?? '';
  }

  /** Returns the stored email or empty string. */
  getCurrentEmail(): string {
    return localStorage.getItem(EMAIL_KEY) ?? '';
  }

  /** Returns the stored role or 'USER' as default. */
  getCurrentRole(): 'USER' | 'ADMIN' {
    return (localStorage.getItem(ROLE_KEY) as 'USER' | 'ADMIN') ?? 'USER';
  }

  /** Returns true if the current user has ADMIN role. */
  isAdmin(): boolean {
    return this.getCurrentRole() === 'ADMIN';
  }

  // ──────────────────────────────────────────────────────────
  // Private helpers
  // ──────────────────────────────────────────────────────────

  private handleAuthSuccess(response: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(USER_KEY, response.username);
    localStorage.setItem(EMAIL_KEY, response.email);
    localStorage.setItem(ROLE_KEY, response.role);
    this.currentUserSubject.next(response.username);
  }

  private getStoredUsername(): string | null {
    return localStorage.getItem(USER_KEY);
  }
}
