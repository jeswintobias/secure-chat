import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

/**
 * Top-level application routes.
 *
 * - /auth/** → Login and Register (public, lazy-loaded)
 * - /chat   → Chat shell (protected by authGuard, lazy-loaded)
 * - /       → Redirects to /chat
 */
export const routes: Routes = [
  {
    path: 'auth',
    loadChildren: () => import('./features/auth/auth.routes').then(m => m.AUTH_ROUTES),
  },
  {
    path: 'chat',
    canActivate: [authGuard],
    loadChildren: () => import('./features/chat/chat.routes').then(m => m.CHAT_ROUTES),
  },
  {
    path: '',
    redirectTo: 'chat',
    pathMatch: 'full',
  },
  {
    path: '**',
    redirectTo: 'chat',
  },
];
