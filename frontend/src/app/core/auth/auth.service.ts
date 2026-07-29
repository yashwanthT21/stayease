import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { jwtDecode } from 'jwt-decode';
import { AuthResponse, CurrentUser, LoginRequest, RegisterRequest } from './auth.models';
import { UserRole } from '../models/enums';

const STORAGE_KEY = 'stayease.auth';

interface JwtPayload {
  sub?: string;
  role?: string;
  userId?: number;
  exp?: number;
}

/**
 * Owns the signed-in identity. Exposes a `user` signal so the whole app (guards,
 * nav, dashboards) reacts to login/logout without manual subscriptions — the
 * right pattern for a zoneless Angular app.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  private readonly _user = signal<CurrentUser | null>(this.restore());

  /** The current identity, or null when signed out. */
  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);
  readonly role = computed<UserRole | null>(() => this._user()?.role ?? null);

  login(body: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', body).pipe(tap((res) => this.store(res)));
  }

  register(body: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', body).pipe(tap((res) => this.store(res)));
  }

  logout(redirect = true): void {
    localStorage.removeItem(STORAGE_KEY);
    this._user.set(null);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  token(): string | null {
    return this._user()?.token ?? null;
  }

  hasAnyRole(roles: readonly UserRole[] | undefined): boolean {
    if (!roles || roles.length === 0) {
      return true;
    }
    const r = this.role();
    return r !== null && roles.includes(r);
  }

  /** True when the stored JWT has an exp claim in the past. */
  isTokenExpired(): boolean {
    const token = this.token();
    if (!token) {
      return true;
    }
    try {
      const { exp } = jwtDecode<JwtPayload>(token);
      return exp !== undefined && exp * 1000 <= Date.now();
    } catch {
      return true;
    }
  }

  private store(res: AuthResponse): void {
    const user: CurrentUser = {
      userId: res.userId,
      email: res.email,
      role: res.role,
      token: res.token,
    };
    localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  private restore(): CurrentUser | null {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    try {
      const user = JSON.parse(raw) as CurrentUser;
      // Drop an expired token on startup.
      const { exp } = jwtDecode<JwtPayload>(user.token);
      if (exp !== undefined && exp * 1000 <= Date.now()) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return user;
    } catch {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
  }
}
