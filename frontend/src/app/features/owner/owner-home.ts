import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { OwnerDataService } from '../../core/services/owner-data.service';
import { OWNER_NAV } from './owner-nav';
import { PropertyResponse } from '../../core/models/dtos';

/**
 * The owner's landing hub: a welcome banner, a handful of portfolio KPIs and a
 * quick-access card for each of the six owner modules.
 *
 * Styling is component-scoped (see `styles` below) so this enhanced look stays
 * confined to the owner dashboard and never touches other roles' screens.
 */
@Component({
  selector: 'app-owner-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './owner-home.html',
})
export class OwnerHomeComponent {
  protected auth = inject(AuthService);
  private data = inject(OwnerDataService);

  protected readonly modules = OWNER_NAV;
  protected readonly loading = signal(true);
  protected readonly properties = signal<PropertyResponse[]>([]);

  /** Friendly first name derived from the email local-part (before the @). */
  protected readonly ownerName = computed(() => {
    const local = (this.auth.user()?.email ?? '').split('@')[0] ?? '';
    return local ? local.charAt(0).toUpperCase() + local.slice(1) : 'there';
  });
  protected readonly initial = computed(() => this.ownerName().charAt(0).toUpperCase() || 'O');
  protected readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  });

  /** KPI cards shown across the portfolio row. */
  protected readonly kpis: { label: string; status?: string; icon: string; tone: string; caption: string }[] = [
    { label: 'Properties', icon: 'bi-houses', tone: 'primary', caption: 'in your portfolio' },
    { label: 'Listed', status: 'LISTED', icon: 'bi-check-circle', tone: 'success', caption: 'live & bookable' },
    { label: 'Unlisted', status: 'UNLISTED', icon: 'bi-eye-slash', tone: 'secondary', caption: 'hidden from guests' },
    { label: 'Maintenance', status: 'UNDER_MAINTENANCE', icon: 'bi-tools', tone: 'warning', caption: 'being serviced' },
  ];

  private readonly toneStops: Record<string, [string, string]> = {
    primary: ['#4f8cff', '#2563eb'],
    success: ['#34d399', '#059669'],
    secondary: ['#94a3b8', '#64748b'],
    warning: ['#fbbf24', '#d97706'],
  };
  private readonly tileStops: [string, string][] = [
    ['#4f8cff', '#2563eb'],
    ['#a855f7', '#6d28d9'],
    ['#22d3ee', '#0891b2'],
    ['#34d399', '#059669'],
    ['#fbbf24', '#d97706'],
    ['#f472b6', '#db2777'],
  ];

  constructor() {
    this.data.myProperties().subscribe({
      next: (props) => {
        this.properties.set(props);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected count(status?: string): number | string {
    if (this.loading()) {
      return '—';
    }
    const rows = this.properties();
    return status ? rows.filter((p) => p.status === status).length : rows.length;
  }

  protected kpiGradient(tone: string): string {
    const [a, b] = this.toneStops[tone] ?? this.toneStops['primary'];
    return `linear-gradient(135deg, ${a}, ${b})`;
  }
  protected tileGradient(i: number): string {
    const [a, b] = this.tileStops[i % this.tileStops.length];
    return `linear-gradient(135deg, ${a}, ${b})`;
  }
}
