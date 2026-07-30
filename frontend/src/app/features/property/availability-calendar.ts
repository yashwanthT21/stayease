import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { OwnerDataService } from '../../core/services/owner-data.service';
import { AvailabilityCalendarResponse, PropertyResponse } from '../../core/models/dtos';
import { AVAILABILITY_STATUSES, AvailabilityStatus } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';

interface DayCell {
  date: string; // yyyy-MM-dd
  day: number;
  entry?: AvailabilityCalendarResponse;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

/**
 * A month-at-a-glance availability calendar for one property. Days are colour
 * coded by status and show the nightly base price; clicking a day opens a small
 * editor that creates or updates that date's availability entry.
 */
@Component({
  selector: 'app-owner-availability-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, CurrencyPipe, OwnerPageHeaderComponent, OwnerDialogComponent],
  templateUrl: './availability-calendar.html',
})
export class AvailabilityCalendarComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private data = inject(OwnerDataService);

  /** When true, the calendar is a pure viewer — days can't be edited. */
  readonly readOnly = input(false);
  /** When set, show only this one property (no picker) — used embedded, e.g. on the guest booking screen. */
  readonly fixedPropertyId = input<number | null>(null);
  /** Embedded = driven by a fixed property (hides the page header + picker). */
  protected readonly embedded = computed(() => this.fixedPropertyId() != null);

  protected readonly weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  protected readonly statuses = AVAILABILITY_STATUSES;

  protected readonly properties = signal<PropertyResponse[]>([]);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly entries = signal<AvailabilityCalendarResponse[]>([]);
  protected readonly loading = signal(false);

  private now = new Date();
  protected readonly year = signal(this.now.getFullYear());
  protected readonly month = signal(this.now.getMonth()); // 0-based

  protected readonly saving = signal(false);
  protected readonly editorOpen = signal(false);
  protected readonly editingDate = signal<string>('');
  protected readonly editingEntryId = signal<number | null>(null);
  protected form: FormGroup = this.buildForm();

  protected readonly monthLabel = computed(() => `${MONTHS[this.month()]} ${this.year()}`);

  private readonly byDate = computed(() => {
    const map = new Map<string, AvailabilityCalendarResponse>();
    for (const e of this.entries()) {
      map.set(String(e.calendarDate).slice(0, 10), e);
    }
    return map;
  });

  /** Weeks of the visible month, padded with nulls for leading/trailing blanks. */
  protected readonly weeks = computed<(DayCell | null)[][]>(() => {
    const y = this.year();
    const m = this.month();
    const map = this.byDate();
    const first = new Date(y, m, 1).getDay(); // 0=Sun
    const daysInMonth = new Date(y, m + 1, 0).getDate();

    const cells: (DayCell | null)[] = [];
    for (let i = 0; i < first; i++) {
      cells.push(null);
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = `${y}-${this.pad(m + 1)}-${this.pad(d)}`;
      cells.push({ date, day: d, entry: map.get(date) });
    }
    while (cells.length % 7 !== 0) {
      cells.push(null);
    }
    const weeks: (DayCell | null)[][] = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7));
    }
    return weeks;
  });

  protected readonly summary = computed(() => {
    const counts: Record<string, number> = { AVAILABLE: 0, BOOKED: 0, BLOCKED: 0, OWNER_USE: 0 };
    for (const e of this.entries()) {
      if (String(e.calendarDate).slice(0, 7) === `${this.year()}-${this.pad(this.month() + 1)}`) {
        counts[e.availabilityStatus] = (counts[e.availabilityStatus] ?? 0) + 1;
      }
    }
    return counts;
  });

  ngOnInit(): void {
    const fixed = this.fixedPropertyId();
    if (fixed) {
      // Embedded single-property viewer (e.g. guest booking screen).
      this.selectedId.set(fixed);
      this.loadEntries(fixed);
      return;
    }
    const preselect = Number(this.route.snapshot.queryParamMap.get('propertyId')) || null;
    this.data.manageableProperties().subscribe({
      next: (props) => {
        this.properties.set(props);
        const initial = preselect && props.some((p) => p.id === preselect) ? preselect : props[0]?.id ?? null;
        if (initial) {
          this.selectedId.set(initial);
          this.loadEntries(initial);
        }
      },
    });
  }

  protected onSelectProperty(event: Event): void {
    const id = Number((event.target as HTMLSelectElement).value) || null;
    this.selectedId.set(id);
    this.entries.set([]);
    if (id) {
      this.loadEntries(id);
    }
  }

  private loadEntries(propertyId: number): void {
    this.loading.set(true);
    this.data.availability(propertyId).subscribe({
      next: (rows) => {
        this.entries.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected prevMonth(): void {
    const m = this.month();
    if (m === 0) {
      this.month.set(11);
      this.year.update((y) => y - 1);
    } else {
      this.month.set(m - 1);
    }
  }

  protected nextMonth(): void {
    const m = this.month();
    if (m === 11) {
      this.month.set(0);
      this.year.update((y) => y + 1);
    } else {
      this.month.set(m + 1);
    }
  }

  protected today(): void {
    const now = new Date();
    this.year.set(now.getFullYear());
    this.month.set(now.getMonth());
  }

  // ---- day editor ----
  protected openDay(cell: DayCell): void {
    if (this.readOnly() || !this.selectedId()) {
      return;
    }
    this.editingDate.set(cell.date);
    this.editingEntryId.set(cell.entry?.id ?? null);
    this.form = this.buildForm(cell.entry);
    this.editorOpen.set(true);
  }

  protected closeEditor(): void {
    this.editorOpen.set(false);
  }

  private buildForm(entry?: AvailabilityCalendarResponse): FormGroup {
    return this.fb.group({
      availabilityStatus: [entry?.availabilityStatus ?? 'AVAILABLE', [Validators.required]],
      basePrice: [entry?.basePrice ?? '', [Validators.required, Validators.min(0.01)]],
    });
  }

  protected saveDay(): void {
    const propertyId = this.selectedId();
    if (this.form.invalid || !propertyId) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue() as { availabilityStatus: AvailabilityStatus; basePrice: number };
    const payload = {
      propertyId,
      calendarDate: this.editingDate(),
      availabilityStatus: raw.availabilityStatus,
      basePrice: Number(raw.basePrice),
      // Minimum-nights was removed with the pricing engine; the backend still
      // requires the field, so we always send 1 (no minimum).
      minimumNights: 1,
    };

    this.saving.set(true);
    const id = this.editingEntryId();
    const req$ =
      id === null
        ? this.crud.create<AvailabilityCalendarResponse>('/api/availability', payload)
        : this.crud.update<AvailabilityCalendarResponse>('/api/availability', id, payload);

    req$.subscribe({
      next: () => {
        this.saving.set(false);
        this.editorOpen.set(false);
        this.toast.success('Availability saved.');
        this.loadEntries(propertyId);
      },
      error: () => this.saving.set(false),
    });
  }

  protected cellClass(cell: DayCell | null): string {
    if (!cell) {
      return 'se-cal-empty';
    }
    if (!cell.entry) {
      return 'se-cal-unset';
    }
    return 'se-cal-' + cell.entry.availabilityStatus.toLowerCase();
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  private pad(n: number): string {
    return String(n).padStart(2, '0');
  }
}
