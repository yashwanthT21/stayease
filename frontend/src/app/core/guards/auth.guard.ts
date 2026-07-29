import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** Blocks a route unless the user is signed in with a non-expired token. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn() && !auth.isTokenExpired()) {
    return true;
  }
  auth.logout(false);
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
