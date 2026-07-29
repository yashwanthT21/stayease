import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { NAV_GROUP_ORDER, RESOURCES } from '../core/registry';
import { ResourceConfig } from '../shared/crud/resource-config';
import { LabelizePipe } from '../shared/pipes/labelize.pipe';
import { OWNER_NAV } from '../features/owner/owner-nav';

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
  protected readonly ownerNav = OWNER_NAV;

  /** Nav items the current role is allowed to see, grouped and ordered. */
  protected readonly navGroups = computed<NavGroup[]>(() => {
    const visible = RESOURCES.filter((r) => !r.hideInNav && this.auth.hasAnyRole(r.roles));
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
