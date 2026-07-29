import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * A single KPI card, shared by every owner dashboard-style row so the numbers
 * read consistently. `accent` is a Bootstrap text-colour class for the value.
 */
@Component({
  selector: 'app-stat-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card se-stat-card shadow-sm h-100">
      <div class="card-body">
        <div class="d-flex align-items-center gap-2 mb-2">
          @if (icon) {
            <span class="se-owner-kpi-icon" [class]="'bg-' + tone + ' bg-opacity-10 text-' + tone">
              <i class="bi {{ icon }}"></i>
            </span>
          }
          <div class="text-muted small text-uppercase fw-semibold">{{ label }}</div>
        </div>
        <div class="h3 fw-bold mb-0" [class]="'text-' + tone">{{ value }}</div>
        @if (hint) {
          <div class="text-muted small mt-1">{{ hint }}</div>
        }
      </div>
    </div>
  `,
})
export class StatTileComponent {
  @Input() label = '';
  @Input() value: string | number | null = '';
  @Input() icon = '';
  /** Bootstrap theme colour name (primary, success, warning, danger, secondary…). */
  @Input() tone = 'primary';
  @Input() hint = '';
}
