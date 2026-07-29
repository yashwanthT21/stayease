import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { RESOURCES } from './core/registry';
import { ShellComponent } from './layout/shell';
import { LoginComponent } from './features/auth/login';
import { RegisterComponent } from './features/auth/register';
import { DashboardComponent } from './features/dashboard/dashboard';
import { ResourcePageComponent } from './shared/components/resource-page';
import { OWNER_ROUTES } from './features/owner/owner.routes';

/**
 * Routes are generated from the resource registry: one CRUD route per resource,
 * each guarded by its declared roles, all nested inside the authenticated shell.
 */
export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      ...OWNER_ROUTES,
      ...RESOURCES.map((r) => ({
        path: r.key,
        component: ResourcePageComponent,
        canActivate: [roleGuard],
        data: { config: r, roles: r.roles },
      })),
      { path: '', pathMatch: 'full' as const, redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
