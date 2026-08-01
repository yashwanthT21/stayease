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
    <div class="d-flex align-items-center justify-content-between flex-wrap gap-3 mb-4">
      <div class="d-flex align-items-center gap-3">
        @if (icon) {
          <span class="se-page-head-icon"><i class="bi {{ icon }}"></i></span>
        }
        <div>
          <h1 class="se-page-head-title mb-0">{{ title }}</h1>
          @if (subtitle) {
            <p class="se-page-head-sub mb-0">{{ subtitle }}</p>
          }
        </div>
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
