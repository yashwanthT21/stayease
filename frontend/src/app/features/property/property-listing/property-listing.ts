import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OwnerDataService } from '../../../core/services/owner-data.service';
import { PropertyResponse } from '../../../core/models/dtos';
import { PropertyStatus, PROPERTY_STATUSES } from '../../../core/models/enums';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../../shared/ui/owner-page-header';

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
  styles: [`
    :host { display: block; }

    .se-pl-card {
      display: flex;
      flex-direction: column;
      background: #fff;
      border: 1px solid var(--se-border);
      border-radius: 1rem;
      overflow: hidden;
      box-shadow: var(--se-shadow-sm);
      transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .se-pl-card:hover {
      transform: translateY(-4px);
      box-shadow: var(--se-shadow-md);
    }

    /* Cover banner */
    .se-pl-cover {
      position: relative;
      height: 84px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      overflow: hidden;
    }
    .se-pl-cover-icon {
      width: 50px;
      height: 50px;
      border-radius: 0.85rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
      background: rgba(255, 255, 255, 0.22);
      border: 1px solid rgba(255, 255, 255, 0.45);
      z-index: 2;
    }
    .se-pl-watermark {
      position: absolute;
      right: -0.5rem;
      bottom: -1.1rem;
      font-size: 4.6rem;
      color: rgba(255, 255, 255, 0.16);
      z-index: 1;
      pointer-events: none;
    }
    .se-pl-status {
      position: absolute;
      top: 0.6rem;
      right: 0.6rem;
      z-index: 3;
    }
    .se-pl-type {
      position: absolute;
      top: 0.6rem;
      left: 0.6rem;
      z-index: 3;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      background: rgba(255, 255, 255, 0.22);
      border: 1px solid rgba(255, 255, 255, 0.4);
      color: #fff;
      padding: 0.1rem 0.55rem;
      border-radius: 2rem;
    }

    /* Body */
    .se-pl-body { padding: 0.8rem 1.1rem 0.6rem; flex: 1 1 auto; }
    .se-pl-title {
      font-size: 1.02rem;
      font-weight: 700;
      color: #0f172a;
      margin-bottom: 0.1rem;
    }
    .se-pl-city { font-size: 0.85rem; color: #64748b; margin-bottom: 0.65rem; }

    /* Stats strip */
    .se-pl-stats {
      display: flex;
      background: #f8fafc;
      border: 1px solid var(--se-border);
      border-radius: 0.7rem;
      overflow: hidden;
      margin-bottom: 0.65rem;
    }
    .se-pl-stat {
      flex: 1 1 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.05rem;
      padding: 0.4rem 0.25rem;
    }
    .se-pl-stat + .se-pl-stat { border-left: 1px solid var(--se-border); }
    .se-pl-stat i { color: #2563eb; font-size: 0.95rem; }
    .se-pl-stat-val { font-weight: 700; color: #0f172a; line-height: 1; }
    .se-pl-stat-lbl {
      font-size: 0.65rem;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #94a3b8;
    }

    /* Amenities */
    .se-pl-amenities { display: flex; flex-wrap: wrap; gap: 0.3rem; }
    .se-pl-amenity {
      font-size: 0.72rem;
      color: #475569;
      background: #eef2ff;
      border: 1px solid #e0e7ff;
      padding: 0.1rem 0.5rem;
      border-radius: 2rem;
    }
    .se-pl-amenity-more { background: #f1f5f9; border-color: var(--se-border); color: #64748b; }

    /* Footer actions */
    .se-pl-foot {
      display: flex;
      gap: 0.5rem;
      padding: 0.6rem 1.1rem 0.75rem;
      border-top: 1px solid var(--se-border);
    }
  `],
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

  /** Split the free-text amenities blob into a clean list of chips. */
  protected amenityList(p: PropertyResponse): string[] {
    if (!p.amenitiesList) {
      return [];
    }
    return p.amenitiesList
      .split(/[,\n;]+/)
      .map((a) => a.trim())
      .filter((a) => a.length > 0);
  }

  /** The first few amenities shown as chips (the rest collapse into "+N more"). */
  protected topAmenities(p: PropertyResponse): string[] {
    return this.amenityList(p).slice(0, 4);
  }
  protected moreAmenities(p: PropertyResponse): number {
    return Math.max(0, this.amenityList(p).length - 4);
  }

  /** A deterministic cover gradient per property type so cards feel distinct. */
  protected typeAccent(type: string): string {
    const stops: Record<string, [string, string]> = {
      APARTMENT: ['#4f8cff', '#2563eb'],
      VILLA: ['#0ea5a4', '#0f766e'],
      COTTAGE: ['#f59e0b', '#b45309'],
      TOWNHOUSE: ['#6366f1', '#4338ca'],
      STUDIO: ['#a855f7', '#6d28d9'],
      BUNGALOW_ROOM: ['#f43f5e', '#be123c'],
    };
    const [a, b] = stops[type] ?? ['#4f8cff', '#2563eb'];
    return `linear-gradient(135deg, ${a}, ${b})`;
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
