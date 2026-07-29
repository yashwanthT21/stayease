import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OwnerDataService } from '../../data/owner-data.service';
import { PropertyResponse } from '../../../../core/models/dtos';
import { PropertyStatus, PROPERTY_STATUSES } from '../../../../core/models/enums';
import { LabelizePipe } from '../../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../ui/owner-page-header';

/**
 * A visual, guest's-eye gallery of the owner's properties. Read-only on purpose
 * — editing lives in the Listing Manager; this screen answers "how do my
 * listings look?".
 */
@Component({
  selector: 'app-owner-property-listing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LabelizePipe, OwnerPageHeaderComponent],
  templateUrl: './property-listing.html',
})
export class PropertyListingComponent {
  private data = inject(OwnerDataService);

  protected readonly loading = signal(true);
  protected readonly properties = signal<PropertyResponse[]>([]);
  protected readonly statusFilter = signal<'ALL' | PropertyStatus>('ALL');
  protected readonly statuses = PROPERTY_STATUSES;

  protected readonly visible = computed(() => {
    const f = this.statusFilter();
    const rows = this.properties();
    return f === 'ALL' ? rows : rows.filter((p) => p.status === f);
  });

  constructor() {
    this.data.myProperties().subscribe({
      next: (props) => {
        this.properties.set(props);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected setFilter(value: 'ALL' | PropertyStatus): void {
    this.statusFilter.set(value);
  }

  /** Split the free-text amenities blob into a handful of chips. */
  protected amenityChips(p: PropertyResponse): string[] {
    if (!p.amenitiesList) {
      return [];
    }
    return p.amenitiesList
      .split(/[,\n;]+/)
      .map((a) => a.trim())
      .filter((a) => a.length > 0)
      .slice(0, 6);
  }

  protected statusBadge(status: PropertyStatus): string {
    switch (status) {
      case 'LISTED':
        return 'text-bg-success';
      case 'UNDER_MAINTENANCE':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  /** A deterministic accent per property type so cards feel distinct. */
  protected typeIcon(type: string): string {
    switch (type) {
      case 'VILLA':
        return 'bi-house-heart';
      case 'APARTMENT':
        return 'bi-building';
      case 'STUDIO':
        return 'bi-door-closed';
      case 'COTTAGE':
        return 'bi-tree';
      case 'TOWNHOUSE':
        return 'bi-buildings';
      default:
        return 'bi-houses';
    }
  }
}
