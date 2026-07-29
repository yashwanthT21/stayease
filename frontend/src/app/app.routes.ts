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
import { AvailabilityCalendarComponent } from './features/owner/pages/calendar/availability-calendar';
import { BrowsePropertiesComponent } from './features/guest/browse-properties';
import { MyReservationsComponent } from './features/guest/my-reservations';
import { GuestProfileComponent } from './features/guest/guest-profile';
import { TurnoverManagerComponent } from './features/manager/turnovers';

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
      // Guest (customer) dashboard: browse & book, and track own reservations.
      { path: 'guest/browse', component: BrowsePropertiesComponent, canActivate: [roleGuard], data: { roles: ['GUEST'] } },
      { path: 'guest/reservations', component: MyReservationsComponent, canActivate: [roleGuard], data: { roles: ['GUEST'] } },
      { path: 'guest/profile', component: GuestProfileComponent, canActivate: [roleGuard], data: { roles: ['GUEST'] } },
      ...OWNER_ROUTES,
      ...RESOURCES.map((r) => ({
        path: r.key,
        // A couple of resources use bespoke screens instead of the generic CRUD:
        //  - availability → the shared month-grid calendar
        //  - turnovers    → the manager housekeeping screen (checked-out only)
        component:
          r.key === 'availability'
            ? AvailabilityCalendarComponent
            : r.key === 'turnovers'
              ? TurnoverManagerComponent
              : ResourcePageComponent,
        canActivate: [roleGuard],
        data: { config: r, roles: r.roles },
      })),
      { path: '', pathMatch: 'full' as const, redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
