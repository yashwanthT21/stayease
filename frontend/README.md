# StayEase — Frontend

An Angular 21 single-page app for the StayEase property-management platform. It
is a thin, config-driven console over the Spring Boot microservices in
`../backend`.

## Stack

- **Angular 21** (standalone components, **zoneless** change detection, signals, `@if`/`@for` control flow)
- **TypeScript** (strict)
- **Bootstrap 5** + Bootstrap Icons (no runtime JS dependency; modals/toasts are signal-driven)
- JWT auth with a functional HTTP interceptor

## Prerequisites

- Node.js 20+ (built/tested on Node 25) and npm
- The backend running (see `../backend/HOW-TO-RUN.md`). You need at least:
  - **api-gateway** — property, availability, pricing, notifications, and the `/api/auth` passthrough
  - the **stayease-backend** monolith (`:8085`) — everything else
  - **eureka-server**, **property-service**, **notification-service**

## Run

```bash
npm install        # first time only
npm start          # ng serve on http://localhost:4200
```

Open http://localhost:4200 and register an account (pick any role) or sign in.

## How it talks to the backend (dev proxy)

The backend is split across services, so the SPA uses relative `/api/...` URLs
and the dev-server proxy (`proxy.conf.js`) forwards each prefix to the right
place — this also avoids browser CORS in development:

| Prefix | Target |
|---|---|
| `/api/properties`, `/api/availability`, `/api/pricing-rules`, `/api/notifications`, `/api/auth` | **api-gateway** |
| everything else under `/api` | **stayease-backend monolith** (`:8085`) |

> ⚠️ **Gateway port.** `proxy.conf.js` defaults the gateway to
> `http://localhost:7000` (from `api-gateway/application.yml`). The backend docs
> mention `8080`. If the gateway runs on a different port, change the single
> `GATEWAY` constant at the top of `proxy.conf.js`.

## Architecture

Almost the entire app is **metadata-driven**. The backend is uniform REST CRUD,
so instead of one component per screen there is:

- `src/app/core/registry.ts` — the single source of truth: one `ResourceConfig`
  per backend resource (fields, list columns, filters, roles, PATCH actions).
- `src/app/shared/components/resource-page.*` — one generic component that renders
  the full CRUD screen (list, filters, search, paging, create/edit modal, delete
  confirm) for **any** resource from its config.
- `src/app/app.routes.ts` — routes are generated from the registry; each is
  role-guarded to mirror the backend's RBAC.
- `src/app/layout/shell.*` — the authenticated frame; its sidebar is built from
  the registry and filtered by the signed-in user's role.

Adding a new backend resource to the UI is usually just **one new entry in
`registry.ts`** — no new component required.

### Key folders

```
src/app/
  core/       auth, http interceptors, guards, models/enums, CrudService, registry
  shared/     resource-config types, the generic resource page, pipes, toast host
  layout/     shell (sidebar + top bar)
  features/   auth (login/register), dashboard
```

## Roles

`OWNER, GUEST, PROPERTY_MANAGER, HOUSEKEEPING, FINANCE, ADMIN`. The nav, route
guards, and dashboard adapt to the signed-in role. Server-side RBAC still
applies — the UI simply avoids offering actions that would 403.

## Build

```bash
npm run build      # outputs to dist/frontend
```
