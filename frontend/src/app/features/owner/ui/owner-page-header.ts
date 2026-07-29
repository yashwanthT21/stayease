import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * The standard title block at the top of every owner page: an icon, a heading,
 * a one-line subtitle and an optional actions slot on the right.
 *
 *   <app-owner-page-header icon="bi-houses" title="Listing Manager" subtitle="…">
 *     <button headerActions class="btn btn-primary btn-sm">New</button>
 *   </app-owner-page-header>
 */
@Component({
  selector: 'app-owner-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-4">
      <div>
        <h1 class="h4 mb-0 d-flex align-items-center gap-2">
          @if (icon) {
            <i class="bi {{ icon }} text-primary"></i>
          }
          {{ title }}
        </h1>
        @if (subtitle) {
          <p class="text-muted mb-0 small">{{ subtitle }}</p>
        }
      </div>
      <ng-content select="[headerActions]"></ng-content>
    </div>
  `,
})
export class OwnerPageHeaderComponent {
  @Input() icon = '';
  @Input() title = '';
  @Input() subtitle = '';
}
