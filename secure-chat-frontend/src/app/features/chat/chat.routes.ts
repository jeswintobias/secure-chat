import { Routes } from '@angular/router';

export const CHAT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./chat-shell/chat-shell.component').then(m => m.ChatShellComponent),
  },
];
