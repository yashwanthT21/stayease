import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin, of, catchError, map } from 'rxjs';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { PropertyResponse, TurnoverAssignmentResponse, TurnoverChecklistResponse } from '../../core/models/dtos';
import { CHECKLIST_CATEGORIES, CHECKLIST_STATUSES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../shared/ui/select-value';

/**
 * Turnover Checklists (Housekeeping domain) — the housekeeper's own screen for
 * the tasks that make up a clean.
 *
 * The generic CRUD engine can't render this usefully: /api/checklists is only
 * addressable per turnover (`?turnoverId=`), and a turnover has no single field
 * that identifies it to a human — the generic reference picker showed nothing but
 * each turnover's status, so every option in the dropdown read the same. Here the
 * turnover picker is built from real turnovers with a readable label (property,
 * date, status), scoped to the ones the signed-in housekeeper was actually
 * assigned, and items for every visible turnover are loaded up front so the
 * filters below work across all of them instead of one at a time.
 */
@Component({
  selector: 'app-turnover-checklist-manager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './turnover-checklist-manager.html',
})
export class TurnoverChecklistManagerComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  private readonly api = '/api/checklists';

  protected readonly categories = CHECKLIST_CATEGORIES;
  protected readonly statuses = CHECKLIST_STATUSES;

  /** Checklists belong to housekeeping; a property manager only reads them. */
  protected readonly canEdit = computed(() => this.auth.role() !== 'PROPERTY_MANAGER');

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly items = signal<TurnoverChecklistResponse[]>([]);
  protected readonly turnovers = signal<TurnoverAssignmentResponse[]>([]);
  protected readonly properties = signal<PropertyResponse[]>([]);

  // ---- filters (all client-side over the loaded items) ----
  protected readonly search = signal('');
  protected readonly turnoverFilter = signal<string>('');
  protected readonly categoryFilter = signal<string>('');
  protected readonly statusFilter = signal<string>('');
  /** '' = any, 'DONE' = completed only, 'TODO' = not completed. */
  protected readonly completionFilter = signal<string>('');

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<TurnoverChecklistResponse | null>(null);
  protected readonly attempted = signal(false);

  // Turnover / category / status are signal-backed selects bound through
  // SelectValueDirective, so a choice survives the turnover list resolving.
  protected readonly selectedTurnoverId = signal<number | null>(null);
  protected readonly selectedCategory = signal<string>('');
  protected readonly selectedStatus = signal<string>('PENDING');

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    let rows = this.items();

    const turnoverId = this.turnoverFilter();
    if (turnoverId) {
      rows = rows.filter((r) => r.turnoverId === Number(turnoverId));
    }
    const category = this.categoryFilter();
    if (category) {
      rows = rows.filter((r) => r.category === category);
    }
    const status = this.statusFilter();
    if (status) {
      rows = rows.filter((r) => r.status === status);
    }
    const completion = this.completionFilter();
    if (completion === 'DONE') {
      rows = rows.filter((r) => r.completed);
    } else if (completion === 'TODO') {
      rows = rows.filter((r) => !r.completed);
    }

    const term = this.search().trim().toLowerCase();
    if (!term) {
      return rows;
    }
    return rows.filter((r) =>
      `${r.taskName} ${r.notes ?? ''} ${r.category} ${r.status} ${this.turnoverLabel(r.turnoverId)}`
        .toLowerCase()
        .includes(term),
    );
  });

  /** Progress across whatever the filters currently show. */
  protected readonly doneCount = computed(() => this.filtered().filter((r) => r.completed).length);

  protected readonly anyFilterActive = computed(
    () =>
      !!this.turnoverFilter() ||
      !!this.categoryFilter() ||
      !!this.statusFilter() ||
      !!this.completionFilter() ||
      !!this.search().trim(),
  );

  constructor() {
    this.load();
  }

  // ---------------- data loading ----------------
  private load(): void {
    this.loading.set(true);
    const role = this.auth.role();
    const userId = this.auth.user()?.userId ?? 0;

    // A housekeeper's turnovers are the ones assigned to them; anyone else sees
    // the turnovers for the properties they can see.
    const turnovers$ =
      role === 'HOUSEKEEPING'
        ? this.crud.list<TurnoverAssignmentResponse>('/api/turnovers', { assignedToId: userId })
        : this.crud.list<TurnoverAssignmentResponse>('/api/turnovers');

    forkJoin({
      properties: this.crud.list<PropertyResponse>('/api/properties').pipe(catchError(() => of([] as PropertyResponse[]))),
      turnovers: turnovers$.pipe(catchError(() => of([] as TurnoverAssignmentResponse[]))),
    }).subscribe({
      next: ({ properties, turnovers }) => {
        // A property manager's /api/properties is already scoped to them, so this
        // keeps their turnover list to their own properties too.
        const visible =
          role === 'PROPERTY_MANAGER'
            ? turnovers.filter((t) => properties.some((p) => p.id === t.propertyId))
            : turnovers;
        this.properties.set(properties);
        this.turnovers.set(visible);
        this.loadItems(visible);
      },
      error: () => this.loading.set(false),
    });
  }

  /**
   * /api/checklists is per-turnover, so "everything I'm responsible for" is a
   * fan-out. One failing turnover must not blank the page, hence the per-request
   * catchError.
   */
  private loadItems(turnovers: TurnoverAssignmentResponse[]): void {
    if (!turnovers.length) {
      this.items.set([]);
      this.loading.set(false);
      return;
    }
    forkJoin(
      turnovers.map((t) =>
        this.crud
          .list<TurnoverChecklistResponse>(this.api, { turnoverId: t.id })
          .pipe(catchError(() => of([] as TurnoverChecklistResponse[]))),
      ),
    )
      .pipe(map((lists) => lists.flat()))
      .subscribe({
        next: (rows) => {
          this.items.set(rows);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private reloadItems(): void {
    this.loadItems(this.turnovers());
  }

  // ---------------- filter handlers ----------------
  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
  protected onTurnoverFilter(value: string): void {
    this.turnoverFilter.set(value);
  }
  protected onCategoryFilter(value: string): void {
    this.categoryFilter.set(value);
  }
  protected onStatusFilter(value: string): void {
    this.statusFilter.set(value);
  }
  protected onCompletionFilter(value: string): void {
    this.completionFilter.set(value);
  }

  protected clearFilters(): void {
    this.turnoverFilter.set('');
    this.categoryFilter.set('');
    this.statusFilter.set('');
    this.completionFilter.set('');
    this.search.set('');
  }

  // ---------------- create / edit ----------------
  private buildForm(row?: TurnoverChecklistResponse): FormGroup {
    return this.fb.group({
      taskName: [row?.taskName ?? '', [Validators.required, Validators.maxLength(150)]],
      completed: [row?.completed ?? false],
      notes: [row?.notes ?? ''],
    });
  }

  protected onTurnoverChange(value: string): void {
    this.selectedTurnoverId.set(value ? Number(value) : null);
  }
  protected onCategoryChange(value: string): void {
    this.selectedCategory.set(value);
  }
  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    // Creating while looking at one turnover defaults to that turnover; if only
    // one turnover exists there's nothing to choose, so pre-select it.
    const filtered = this.turnoverFilter();
    const only = this.turnovers().length === 1 ? this.turnovers()[0].id : null;
    this.selectedTurnoverId.set(filtered ? Number(filtered) : only);
    this.selectedCategory.set('');
    this.selectedStatus.set('PENDING');
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: TurnoverChecklistResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedTurnoverId.set(row.turnoverId ?? null);
    this.selectedCategory.set(row.category);
    this.selectedStatus.set(row.status);
    this.form = this.buildForm(row);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected turnoverMissing(): boolean {
    return this.attempted() && this.selectedTurnoverId() == null;
  }
  protected categoryMissing(): boolean {
    return this.attempted() && !this.selectedCategory();
  }

  protected submit(): void {
    this.attempted.set(true);
    const turnoverId = this.selectedTurnoverId();
    if (this.form.invalid || turnoverId == null || !this.selectedCategory()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as { taskName: string; completed: boolean; notes: string };
    const payload: Record<string, unknown> = {
      turnoverId,
      taskName: raw.taskName,
      category: this.selectedCategory(),
      completed: !!raw.completed,
      status: this.selectedStatus(),
    };
    if (raw.notes) {
      payload['notes'] = raw.notes;
    }

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<TurnoverChecklistResponse>(this.api, payload)
        : this.crud.update<TurnoverChecklistResponse>(this.api, id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Checklist item ${id === null ? 'created' : 'updated'}.`);
        this.upsert(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) from the authoritative server response. */
  private upsert(row: TurnoverChecklistResponse): void {
    this.items.update((rows) => {
      const idx = rows.findIndex((r) => r.id === row.id);
      if (idx === -1) {
        return [row, ...rows];
      }
      const copy = [...rows];
      copy[idx] = row;
      return copy;
    });
  }

  /** Tick a task off in place, without opening the editor. */
  protected toggleCompleted(row: TurnoverChecklistResponse): void {
    const completed = !row.completed;
    this.crud
      .update<TurnoverChecklistResponse>(this.api, row.id, {
        turnoverId: row.turnoverId,
        taskName: row.taskName,
        category: row.category,
        completed,
        notes: row.notes,
        // Ticking the last box off is the whole point of the screen, so the item's
        // status follows its completion rather than needing a second edit.
        status: completed ? 'DONE' : 'PENDING',
      })
      .subscribe({
        next: (saved) => this.upsert(saved),
        error: () => this.reloadItems(),
      });
  }

  // ---------------- delete ----------------
  protected askDelete(row: TurnoverChecklistResponse): void {
    this.deleteTarget.set(row);
  }
  protected cancelDelete(): void {
    this.deleteTarget.set(null);
  }
  protected confirmDelete(): void {
    const row = this.deleteTarget();
    if (!row) {
      return;
    }
    this.crud.remove(this.api, row.id).subscribe({
      next: () => {
        this.items.update((rows) => rows.filter((r) => r.id !== row.id));
        this.deleteTarget.set(null);
        this.toast.success('Checklist item deleted.');
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // ---------------- display helpers ----------------
  protected propertyTitle(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const p = this.properties().find((x) => x.id === Number(id));
    return p ? p.title : `#${id}`;
  }

  /** "Sea Breeze Villa · 12 Aug 2026 · Pending" — enough to tell turnovers apart. */
  protected turnoverLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const t = this.turnovers().find((x) => x.id === Number(id));
    if (!t) {
      return `Turnover #${id}`;
    }
    const parts = [this.propertyTitle(t.propertyId)];
    if (t.assignedDate) {
      parts.push(this.fmtDate(t.assignedDate));
    }
    parts.push(this.title(t.status));
    return parts.join(' · ');
  }

  /** "2026-08-12" → "12 Aug 2026". */
  private fmtDate(date: string): string {
    const d = new Date(String(date).slice(0, 10) + 'T00:00:00');
    return Number.isNaN(d.getTime())
      ? String(date)
      : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private title(value: string): string {
    return value
      .toLowerCase()
      .split('_')
      .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
      .join(' ');
  }

  protected statusBadge(status: string): string {
    return status === 'DONE' ? 'text-bg-success' : 'text-bg-secondary';
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }
}
