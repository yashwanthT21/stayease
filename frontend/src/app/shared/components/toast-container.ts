import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-toast-container',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="se-toast-container">
      @for (t of toastService.toasts(); track t.id) {
        <div class="toast show align-items-center text-bg-{{ t.kind }} border-0" role="alert">
          <div class="d-flex">
            <div class="toast-body">{{ t.text }}</div>
            <button
              type="button"
              class="btn-close btn-close-white me-2 m-auto"
              aria-label="Close"
              (click)="toastService.dismiss(t.id)"
            ></button>
          </div>
        </div>
      }
    </div>
  `,
})
export class ToastContainerComponent {
  protected toastService = inject(ToastService);
}
