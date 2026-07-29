import { UserRole } from '../models/enums';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
  phone?: string;
  role: UserRole;
}

/** Returned by /api/auth/register and /login. */
export interface AuthResponse {
  token: string;
  tokenType: string; // always "Bearer"
  userId: number;
  email: string;
  role: UserRole;
}

/** The signed-in identity we keep in memory + localStorage. */
export interface CurrentUser {
  userId: number;
  email: string;
  role: UserRole;
  token: string;
}
