# `iam` — Identity & Access Management

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/iam`
(entities `User`, `AuditLog`) plus the JWT/auth concerns in
`backend/api-gateway` security.
**Nav group:** Administration  •  **Owner (fill in):** ________

Sign-in / registration and the admin user & audit screens.

## Layout
One folder per screen:

```
iam/
├── login/     login.ts · login.html
└── register/  register.ts · register.html
```

## Bespoke screens
- `login/` — email + password sign-in.
- `register/` — self-registration with role selection.

## Registry-driven screens (rendered by `shared/components/resource-page.ts`)
Declared in `core/registry.ts` under the **Administration** group:
- `users` → `/api/users` — full user CRUD (ADMIN).
- `audit-logs` → `/api/audit-logs` — read-only audit trail (ADMIN, FINANCE).

## Shared auth runtime (used app-wide, not only here)
The token storage, current-user state, guards and interceptors that back this
module live in shared infra: `core/auth/`, `core/guards/`, `core/interceptors/`.
