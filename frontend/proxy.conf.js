/**
 * Dev-server proxy.
 *
 * <p>The browser talks to the dev server's own origin and the dev server
 * forwards /api to the backend. Two things fall out of that: the API port is
 * no longer baked into a tracked source file (set API_URL to override it), and
 * development is same-origin, so CORS never enters the picture locally.
 */
module.exports = {
  '/api': {
    target: process.env['API_URL'] || 'http://localhost:8080',
    changeOrigin: true,
    secure: false,
  },
};
