/**
 * Development configuration.
 *
 * <p>A relative base URL on purpose: the dev server proxies /api to the backend
 * (see proxy.conf.js), so this file carries no host or port and nothing
 * machine-specific ends up in version control. Point it elsewhere with
 * API_URL=http://localhost:8081 npm start
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api',
};
