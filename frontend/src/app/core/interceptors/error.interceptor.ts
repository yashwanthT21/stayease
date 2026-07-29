import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ToastService } from '../services/toast.service';
import { ApiError } from '../models/dtos';

/**
 * Central HTTP error handling:
 *  - 401 -> the token is missing/expired: sign out and bounce to login.
 *  - 403 -> authenticated but not allowed for this role.
 *  - otherwise surface the backend message as a toast.
 * The original error still propagates so callers can react if they want.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // Let the login/register screens render their own message.
      const isAuthCall = req.url.includes('/api/auth/');

      if (err.status === 401 && !isAuthCall) {
        toast.error('Your session has expired. Please sign in again.');
        auth.logout();
      } else if (err.status === 403) {
        toast.error('You do not have permission to perform that action.');
      } else if (err.status === 0) {
        toast.error('Cannot reach the server. Is the backend running?');
      } else if (!isAuthCall) {
        toast.error(extractMessage(err));
      }
      return throwError(() => err);
    }),
  );
};

function extractMessage(err: HttpErrorResponse): string {
  const body = err.error as ApiError | string | undefined;
  if (typeof body === 'string' && body.trim()) {
    return body;
  }
  if (body && typeof body === 'object') {
    if (body.fieldErrors && Object.keys(body.fieldErrors).length) {
      return Object.entries(body.fieldErrors)
        .map(([k, v]) => `${k}: ${v}`)
        .join('; ');
    }
    if (body.message) {
      return body.message;
    }
    if (body.error) {
      return body.error;
    }
  }
  return `Request failed (${err.status || 'network error'}).`;
}
