import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { CrudService } from '../../../core/services/crud.service';
import { TurnoverAssignmentResponse, TurnoverChecklistResponse } from '../../../core/models/dtos';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';

/**
 * Read-only checklist viewer for a single turnover (Housekeeping domain).
 *
 * Rendered as a modal by TurnoverAssignmentComponent: when it is handed a
 * turnover via [turnover], it loads that turnover's items from /api/checklists
 * and shows them; a null input hides the modal, and closing emits (close).
 *
 * This is the checklist half of the former single turnovers screen, extracted
 * so the assignment and checklist concerns each live in their own component —
 * behaviour is unchanged (same click → same modal → same request).
 */
@Component({
  selector: 'app-turnover-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, OwnerDialogComponent],
  templateUrl: './turnover-checklist.html',
})
export class TurnoverChecklistComponent {
  private crud = inject(CrudService);

  /** The turnover whose checklist is shown (null hides the modal). */
  protected readonly selected = signal<TurnoverAssignmentResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly items = signal<TurnoverChecklistResponse[]>([]);

  /** Resolved property title for the modal header, supplied by the parent. */
  @Input() propertyTitle = '';

  /**
   * Setting a turnover loads its checklist items; setting null just clears.
   * (Kept as a setter so the load fires exactly when the parent opens it —
   * the same moment the old `viewChecklist()` used to run.)
   */
  @Input() set turnover(t: TurnoverAssignmentResponse | null) {
    this.selected.set(t);
    this.items.set([]);
    if (t) {
      this.loading.set(true);
      this.crud.list<TurnoverChecklistResponse>('/api/checklists', { turnoverId: t.id }).subscribe({
        next: (items) => {
          this.items.set(items);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }
  }

  @Output() close = new EventEmitter<void>();

  protected hkBadge(status: string): string {
    return status === 'COMPLETED' ? 'text-bg-success' : 'text-bg-secondary';
  }
}
