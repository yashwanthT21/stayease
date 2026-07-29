/*
 * Dev-server proxy for StayEase.
 *
 * The backend is a set of microservices, so the SPA talks to TWO origins:
 *   - api-gateway  -> property-service, notification-service, and the /api/auth
 *                     passthrough to the IAM monolith.
 *   - the monolith -> everything else (users, reservations, guests, stays,
 *                     housekeeping, maintenance, finance, audit).
 *
 * All app code uses relative "/api/..." URLs; this file decides where each
 * prefix actually goes so we never hit browser CORS in development.
 *
 * NOTE ON PORTS: api-gateway/src/main/resources/application.yml sets the gateway
 * port to 7000, but HOW-TO-RUN.md documents 8080. If the gateway is running on a
 * different port, change GATEWAY below (that is the only line to touch).
 */
const GATEWAY = 'http://localhost:7000'; // api-gateway
const MONOLITH = 'http://localhost:8085'; // stayease-backend monolith

// Order matters: the specific gateway prefixes are matched before the "/api"
// catch-all that forwards everything else to the monolith.
module.exports = {
  '/api/properties': { target: GATEWAY, secure: false, changeOrigin: true },
  '/api/availability': { target: GATEWAY, secure: false, changeOrigin: true },
  '/api/pricing-rules': { target: GATEWAY, secure: false, changeOrigin: true },
  '/api/notifications': { target: GATEWAY, secure: false, changeOrigin: true },
  '/api/auth': { target: GATEWAY, secure: false, changeOrigin: true },
  '/api': { target: MONOLITH, secure: false, changeOrigin: true },
};
