import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { LowerCasePipe } from '@angular/common';
import { AuthService } from '../../core/auth/auth.service';
import { NAV_GROUP_ORDER, RESOURCES } from '../../core/registry';
import { ResourceConfig } from '../../shared/crud/resource-config';
import { UserRole } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';

interface NavGroup {
  group: string;
  items: ResourceConfig[];
}

const ROLE_BLURB: Record<UserRole, string> = {
  OWNER: 'Manage your properties, availability, pricing and keep an eye on bookings.',
  GUEST: 'View your reservations, check-ins and leave reviews for your stays.',
  PROPERTY_MANAGER: 'Coordinate properties, bookings, turnovers and maintenance day to day.',
  HOUSEKEEPING: 'Work through turnover assignments and their cleaning checklists.',
  FINANCE: 'Generate owner statements, process payouts and review the audit trail.',
  ADMIN: 'Full access — manage users, review audit logs and every operational module.',
};

@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LabelizePipe, LowerCasePipe],
  templateUrl: './dashboard.html',
})
export class DashboardComponent implements OnInit {
  protected auth = inject(AuthService);
  private router = inject(Router);

  ngOnInit(): void {
    // Owners and guests have bespoke dashboards — send them there instead of the
    // generic module grid. Other roles stay on this dashboard as before.
    const role = this.auth.role();
    if (role === 'OWNER') {
      void this.router.navigateByUrl('/owner');
    } else if (role === 'GUEST') {
      void this.router.navigateByUrl('/guest/browse');
    }
  }

  protected readonly blurb = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_BLURB[role] : '';
  });

  protected readonly groups = computed<NavGroup[]>(() => {
    const visible = RESOURCES.filter((r) => this.auth.hasAnyRole(r.roles));
    const out: NavGroup[] = [];
    for (const name of NAV_GROUP_ORDER) {
      const items = visible.filter((r) => r.group === name);
      if (items.length) {
        out.push({ group: name, items });
      }
    }
    return out;
  });

  protected readonly totalModules = computed(() => this.groups().reduce((n, g) => n + g.items.length, 0));
}
