import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../services/toast.service';
import { UserRole } from '../models/enums';
import { ResourceConfig } from '../../shared/crud/resource-config';
import { canRoleUseResource } from '../registry';

/**
 * Mirrors the backend's URL-based RBAC. Reads `data.roles` off the route; if
 * absent, any authenticated user may enter. Keeps the UI from offering actions
 * the API would reject with 403 anyway.
 *
 * Resource routes additionally honour ROLE_RESOURCE_ALLOWLIST, so a role with a
 * deliberately narrow workspace (housekeeping, finance) can't reach the screens
 * its sidebar hides by typing the URL.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const roles = route.data['roles'] as readonly UserRole[] | undefined;
  const config = route.data['config'] as ResourceConfig | undefined;
  const allowed = auth.hasAnyRole(roles) && (!config || canRoleUseResource(auth.role(), config.key));
  if (allowed) {
    return true;
  }
  toast.error('You do not have access to that page.');
  return router.createUrlTree(['/dashboard']);
};
