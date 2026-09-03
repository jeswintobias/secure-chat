/**
 * Development environment configuration.
 * API calls are proxied through the Angular dev server (see proxy.conf.json)
 * to avoid CORS issues regardless of what port ng serve uses.
 */
export const environment = {
  production: false,
  apiUrl: '/api',
  wsUrl: '/ws',
  googleClientId: '938545969864-2s84mo2hnjopdsm1dt5kighlhnr9pp1b.apps.googleusercontent.com',
};
