import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * A single, consistent modal shell for every owner screen. It renders the
 * backdrop, centred/scrollable dialog and a title bar; the caller projects the
 * body/footer (usually a <form>) into the content slot, so form submission and
 * validation stay with the caller.
 *
 * Usage:
 *   <app-owner-dialog title="New property" icon="bi-houses" (dismiss)="close()">
 *     <form (ngSubmit)="save()">
 *       <div class="modal-body">…</div>
 *       <div class="modal-footer">…</div>
 *     </form>
 *   </app-owner-dialog>
 */
@Component({
  selector: 'app-owner-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="se-modal-backdrop"></div>
    <!-- Bootstrap's .modal class carries the --bs-modal-* variables the dialog
         parts rely on; d-block forces it visible (it is display:none by default). -->
    <div class="modal d-block se-modal" (click)="onOverlayClick($event)">
      <div class="modal-dialog {{ size === 'lg' ? 'modal-lg' : '' }} modal-dialog-centered modal-dialog-scrollable">
        <div class="modal-content border-0 shadow-lg se-owner-dialog">
          <div class="modal-header bg-light">
            <h5 class="modal-title d-flex align-items-center gap-2">
              @if (icon) {
                <i class="bi {{ icon }} text-primary"></i>
              }
              {{ title }}
            </h5>
            <button type="button" class="btn-close" aria-label="Close" (click)="dismiss.emit()"></button>
          </div>
          <ng-content></ng-content>
        </div>
      </div>
    </div>
  `,
})
export class OwnerDialogComponent {
  @Input() title = '';
  @Input() icon = '';
  @Input() size: 'md' | 'lg' = 'lg';
  /** Emitted on close button or a click on the dimmed area outside the dialog. */
  @Output() dismiss = new EventEmitter<void>();

  onOverlayClick(event: MouseEvent): void {
    // Only when the click lands on the overlay itself, not the dialog.
    if (event.target === event.currentTarget) {
      this.dismiss.emit();
    }
  }
}
