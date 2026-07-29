import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

/**
 * Attaches `Authorization: Bearer <token>` to every API call except the public
 * auth endpoints (which have no token yet).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isAuthCall = req.url.includes('/api/auth/');

  if (token && !isAuthCall) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};
