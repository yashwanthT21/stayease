import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../services/toast.service';
import { UserRole } from '../models/enums';

/**
 * Mirrors the backend's URL-based RBAC. Reads `data.roles` off the route; if
 * absent, any authenticated user may enter. Keeps the UI from offering actions
 * the API would reject with 403 anyway.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const roles = route.data['roles'] as readonly UserRole[] | undefined;
  if (auth.hasAnyRole(roles)) {
    return true;
  }
  toast.error('You do not have access to that page.');
  return router.createUrlTree(['/dashboard']);
};
