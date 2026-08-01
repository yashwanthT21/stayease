import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * A single KPI card, shared by every owner dashboard-style row so the numbers
 * read consistently. `accent` is a Bootstrap text-colour class for the value.
 */
@Component({
  selector: 'app-stat-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="se-kpi h-100">
      <div class="se-kpi-top">
        @if (icon) {
          <span class="se-kpi-icon" [style.background]="gradient()"><i class="bi {{ icon }}"></i></span>
        }
        <span class="se-kpi-label">{{ label }}</span>
      </div>
      <div class="se-kpi-value">{{ value }}</div>
      @if (hint) {
        <div class="se-kpi-caption">{{ hint }}</div>
      }
    </div>
  `,
})
export class StatTileComponent {
  @Input() label = '';
  @Input() value: string | number | null = '';
  @Input() icon = '';
  /** Theme colour name (primary, success, warning, danger, secondary, info). */
  @Input() tone = 'primary';
  @Input() hint = '';

  private readonly toneStops: Record<string, [string, string]> = {
    primary: ['#4f8cff', '#2563eb'],
    success: ['#34d399', '#059669'],
    secondary: ['#94a3b8', '#64748b'],
    warning: ['#fbbf24', '#d97706'],
    danger: ['#fb7185', '#e11d48'],
    info: ['#22d3ee', '#0891b2'],
  };

  protected gradient(): string {
    const [a, b] = this.toneStops[this.tone] ?? this.toneStops['primary'];
    return `linear-gradient(135deg, ${a}, ${b})`;
  }
}
