import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { OwnerDataService } from '../../core/services/owner-data.service';
import { OWNER_NAV } from './owner-nav';
import { PropertyResponse } from '../../core/models/dtos';
import { StatTileComponent } from '../../shared/ui/stat-tile';

/**
 * The owner's landing hub: a welcome banner, a handful of portfolio KPIs and a
 * quick-access card for each of the six owner modules.
 */
@Component({
  selector: 'app-owner-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatTileComponent],
  templateUrl: './owner-home.html',
})
export class OwnerHomeComponent {
  protected auth = inject(AuthService);
  private data = inject(OwnerDataService);

  protected readonly modules = OWNER_NAV;
  protected readonly loading = signal(true);
  protected readonly properties = signal<PropertyResponse[]>([]);

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
}
