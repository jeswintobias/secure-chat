/**
 * Development environment configuration.
 * API calls are proxied through the Angular dev server (see proxy.conf.json)
 * to avoid CORS issues regardless of what port ng serve uses.
 */
export const environment = {
  production: false,
  apiUrl: '/api',
  wsUrl: '/ws',
};
