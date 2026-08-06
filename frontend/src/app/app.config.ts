import { ApplicationConfig, DEFAULT_CURRENCY_CODE, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    // StayEase trades in Indian Rupees, so every `| currency` in the app renders
    // ₹ instead of Angular's built-in USD default. Hand-rolled money formatting
    // uses the RUPEE constant in shared/money.ts for the same reason.
    { provide: DEFAULT_CURRENCY_CODE, useValue: 'INR' },
  ],
};
