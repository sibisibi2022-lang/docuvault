/**
 * Production configuration.
 *
 * <p>The API base URL is baked in at build time through Angular's
 * fileReplacements, which is why the original's nine hardcoded
 * "http://localhost:8080" strings across eight files are gone.
 */
export const environment = {
  production: true,
  apiBaseUrl: 'https://docuvault-api.onrender.com/api',
};
