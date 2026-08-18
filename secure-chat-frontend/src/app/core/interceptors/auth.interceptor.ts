import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

/**
 * Functional HTTP interceptor that appends the JWT bearer token
 * to every outgoing HTTP request's Authorization header.
 *
 * Registered in app.config.ts via provideHttpClient(withInterceptors([...])).
 *
 * Skips injection for requests to external URLs (only intercepts
 * requests matching the application's API base URL).
 * 
 * Also intercepts 401 Unauthorized responses to automatically log out
 * the user when their token expires.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  let modifiedReq = req;

  if (token) {
    modifiedReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
  }

  return next(modifiedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If the backend returns 401 Unauthorized or 403 Forbidden, the token is invalid, expired, or the user was deleted.
      // Automatically log the user out to clear the stale token and redirect to login.
      if (error.status === 401 || error.status === 403) {
        authService.logout();
      }
      return throwError(() => error);
    })
  );
};
