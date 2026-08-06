import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { NAV_GROUP_ORDER, RESOURCES, canRoleUseResource } from '../core/registry';
import { ResourceConfig } from '../shared/crud/resource-config';
import { LabelizePipe } from '../shared/pipes/labelize.pipe';
import { OWNER_NAV } from '../features/owner/owner-nav';
import { GUEST_NAV } from '../features/guest/guest-nav';

interface NavGroup {
  group: string;
  items: ResourceConfig[];
}

/** The authenticated app frame: role-aware sidebar + top bar + routed content. */
@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LabelizePipe],
  templateUrl: './shell.html',
})
export class ShellComponent {
  protected auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  protected readonly sidebarOpen = signal(true);

  /** Owners get a bespoke sidebar; every other role keeps the registry nav. */
  protected readonly isOwner = computed(() => this.auth.role() === 'OWNER');
  protected readonly isGuest = computed(() => this.auth.role() === 'GUEST');
  protected readonly ownerNav = OWNER_NAV;
  protected readonly guestNav = GUEST_NAV;

  /** Nav items the current role is allowed to see, grouped and ordered. */
  protected readonly navGroups = computed<NavGroup[]>(() => {
    // Roles with a fixed workspace (housekeeping, finance) only get their own
    // resources — see ROLE_RESOURCE_ALLOWLIST in core/registry.ts.
    const role = this.auth.role();
    const visible = RESOURCES.filter(
      (r) => !r.hideInNav && this.auth.hasAnyRole(r.roles) && canRoleUseResource(role, r.key),
    );
    const groups: NavGroup[] = [];
    for (const name of NAV_GROUP_ORDER) {
      const items = visible.filter((r) => r.group === name);
      if (items.length) {
        groups.push({ group: name, items });
      }
    }
    return groups;
  });

  toggleMenu(): void {
    this.menuOpen.update((v) => !v);
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
  }
}
