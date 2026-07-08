import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Functional HTTP interceptor that appends the JWT bearer token
 * to every outgoing HTTP request's Authorization header.
 *
 * Registered in app.config.ts via provideHttpClient(withInterceptors([...])).
 *
 * Skips injection for requests to external URLs (only intercepts
 * requests matching the application's API base URL).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token) {
    const cloned = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    });
    return next(cloned);
  }

  return next(req);
};
