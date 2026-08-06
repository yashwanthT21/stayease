import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { RESOURCES } from './core/registry';
import { ShellComponent } from './layout/shell';
import { LoginComponent } from './features/iam/login';
import { RegisterComponent } from './features/iam/register';
import { DashboardComponent } from './features/dashboard/dashboard';
import { ResourcePageComponent } from './shared/components/resource-page';
import { OWNER_ROUTES } from './features/owner/owner.routes';
import { AvailabilityCalendarComponent } from './features/property/availability-calendar';
import { BrowsePropertiesComponent } from './features/property/browse-properties';
import { MyReservationsComponent } from './features/booking/my-reservations';
import { GuestProfileComponent } from './features/booking/guest-profile';
import { TurnoverAssignmentComponent } from './features/housekeeping/turnover-assignment';
import { TurnoverChecklistManagerComponent } from './features/housekeeping/turnover-checklist-manager';
import { CheckInComponent } from './features/stay/check-in';
import { CheckOutComponent } from './features/stay/check-out';
import { StatementBuilderComponent } from './features/finance/statement-builder';
import { MaintenanceIssueComponent } from './features/maintenance/maintenance-issue';
import { PreventiveMaintenanceComponent } from './features/maintenance/preventive-maintenance';

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
        // Some resources use bespoke screens instead of the generic CRUD:
        //  - availability          → the shared month-grid calendar
        //  - turnovers             → the housekeeping turnover-assignment screen
        //  - checklists            → the housekeeper's turnover-checklist screen
        //  - check-ins/check-outs  → the stay module's hand-written CRUD screens
        //  - owner-statements      → the finance statement-builder (auto-derives amounts)
        //  - maintenance-issues / preventive-maintenance → the maintenance module's screens
        component:
          r.key === 'availability'
            ? AvailabilityCalendarComponent
            : r.key === 'turnovers'
              ? TurnoverAssignmentComponent
              : r.key === 'checklists'
                ? TurnoverChecklistManagerComponent
                : r.key === 'check-ins'
                  ? CheckInComponent
                  : r.key === 'check-outs'
                    ? CheckOutComponent
                    : r.key === 'owner-statements'
                      ? StatementBuilderComponent
                      : r.key === 'maintenance-issues'
                        ? MaintenanceIssueComponent
                        : r.key === 'preventive-maintenance'
                          ? PreventiveMaintenanceComponent
                          : ResourcePageComponent,
        canActivate: [roleGuard],
        data: { config: r, roles: r.roles },
      })),
      { path: '', pathMatch: 'full' as const, redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: '' },
];
