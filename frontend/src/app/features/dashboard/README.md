# `dashboard` — Post-login landing (cross-cutting)

**Not a backend module.** The shared landing page shown right after sign-in.

## Files
- `dashboard.ts` / `.html` — role-aware hub that lists the resource groups the
  signed-in user can access (built from `core/registry.ts`).
