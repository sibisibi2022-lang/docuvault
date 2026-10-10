/**
 * Configuration for the container image.
 *
 * <p>A relative base URL: nginx serves the app and proxies /api to the backend
 * container, so the browser only ever talks to one origin and CORS does not
 * apply. The Netlify build uses environment.ts instead, where the API lives on
 * a different host and needs its absolute URL.
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api',
};
