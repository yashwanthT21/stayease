import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { CrudService } from '../../../core/services/crud.service';
import { ToastService } from '../../../core/services/toast.service';
import { OwnerDataService } from '../../../core/services/owner-data.service';
import { AvailabilityCalendarResponse, PropertyResponse } from '../../../core/models/dtos';
import { AVAILABILITY_STATUSES, AvailabilityStatus } from '../../../core/models/enums';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../../shared/ui/select-value';

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
 *
 * Days before today are read-only for everyone — an owner or manager can set
 * status/price from today forward, and a guest can only pick today or later. Past
 * days are greyed out and their cells are disabled, so the rule is visible rather
 * than only enforced on submit. The backend rejects past dates as well, since a
 * disabled button is a courtesy, not a guarantee.
 */
@Component({
  selector: 'app-owner-availability-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, CurrencyPipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
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
  /** Opt-in date picking: tapping an AVAILABLE day emits its date (guest booking). */
  readonly selectable = input(false);
  readonly dateSelected = output<string>();
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
  // Status is a native <select> — signal + (change) rather than formControlName
  // so the first choice registers reliably in this zoneless app.
  protected readonly selectedStatus = signal<string>('AVAILABLE');
  protected form: FormGroup = this.buildForm();

  // ---- drag-to-select multiple days + group editor ----
  protected readonly selectedDates = signal<Set<string>>(new Set());
  protected readonly bulkOpen = signal(false);
  protected readonly bulkStatus = signal<string>('AVAILABLE');
  protected readonly bulkPrice = signal<string>('');
  protected readonly bulkSaving = signal(false);
  protected readonly bulkTried = signal(false);
  private dragging = false;
  private dragAnchor: string | null = null;
  private dragMoved = false;
  private suppressClick = false;

  protected readonly monthLabel = computed(() => `${MONTHS[this.month()]} ${this.year()}`);

  /**
   * Today as yyyy-MM-dd, read fresh each time rather than cached at construction
   * so a tab left open overnight doesn't keep treating yesterday as editable.
   * Dates are compared as ISO strings, which sort chronologically.
   */
  protected todayDate(): string {
    const now = new Date();
    return `${now.getFullYear()}-${this.pad(now.getMonth() + 1)}-${this.pad(now.getDate())}`;
  }

  /** True for a day that has already passed (today itself is still editable). */
  protected isPast(date: string): boolean {
    return date < this.todayDate();
  }

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

  // Bound through SelectValueDirective so the picked property/status survives the
  // options list rendering (or re-rendering) after the value was set.
  protected onSelectProperty(value: string): void {
    const id = Number(value) || null;
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

  // ---- drag-to-select multiple days ----
  protected onCellMouseDown(cell: DayCell): void {
    if (this.readOnly() || !this.selectedId() || this.isPast(cell.date)) {
      return;
    }
    this.dragging = true;
    this.dragMoved = false;
    this.dragAnchor = cell.date;
    this.selectedDates.set(new Set([cell.date]));
  }

  protected onCellMouseEnter(cell: DayCell): void {
    if (!this.dragging || !this.dragAnchor) {
      return;
    }
    this.dragMoved = true;
    this.selectedDates.set(this.rangeBetween(this.dragAnchor, cell.date));
  }

  @HostListener('document:mouseup')
  protected onDocMouseUp(): void {
    if (!this.dragging) {
      return;
    }
    this.dragging = false;
    if (this.dragMoved && this.selectedDates().size > 1) {
      // A real drag across several days → open the group editor. Suppress the
      // click that follows so the single-day editor doesn't also open.
      this.suppressClick = true;
      this.openBulk();
    } else {
      this.selectedDates.set(new Set());
    }
  }

  protected isSelected(date: string): boolean {
    return this.selectedDates().has(date);
  }

  /**
   * Every date from anchor to target inclusive (order-independent), clamped to
   * start no earlier than today — dragging back over past days selects from today
   * onwards instead of picking up days nobody may edit.
   */
  private rangeBetween(a: string, b: string): Set<string> {
    const today = this.todayDate();
    const start = (a <= b ? a : b) < today ? today : a <= b ? a : b;
    const end = a <= b ? b : a;
    const out = new Set<string>();
    if (end < start) {
      return out;
    }
    const d = new Date(start + 'T00:00:00');
    const last = new Date(end + 'T00:00:00');
    while (d <= last) {
      out.add(`${d.getFullYear()}-${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())}`);
      d.setDate(d.getDate() + 1);
    }
    return out;
  }

  // ---- group (bulk) editor ----
  protected openBulk(): void {
    this.bulkTried.set(false);
    this.bulkStatus.set('AVAILABLE');
    this.bulkPrice.set('');
    this.bulkOpen.set(true);
  }

  protected closeBulk(): void {
    this.bulkOpen.set(false);
    this.selectedDates.set(new Set());
  }

  protected onBulkStatus(value: string): void {
    this.bulkStatus.set(value);
  }

  protected onBulkPrice(event: Event): void {
    this.bulkPrice.set((event.target as HTMLInputElement).value);
  }

  /** Create/update an availability entry for every selected date, in one go. */
  protected saveBulk(): void {
    this.bulkTried.set(true);
    const propertyId = this.selectedId();
    const price = Number(this.bulkPrice());
    // Belt-and-braces: the grid already refuses to select past days, so this only
    // matters if the selection was made just before midnight.
    const dates = [...this.selectedDates()].filter((d) => !this.isPast(d));
    if (!propertyId || !(price > 0) || dates.length === 0) {
      return;
    }
    const byDate = this.byDate();
    const reqs = dates.map((date) => {
      const payload = {
        propertyId,
        calendarDate: date,
        availabilityStatus: this.bulkStatus() as AvailabilityStatus,
        basePrice: price,
        minimumNights: 1,
      };
      const existing = byDate.get(date);
      return existing
        ? this.crud.update<AvailabilityCalendarResponse>('/api/availability', existing.id, payload)
        : this.crud.create<AvailabilityCalendarResponse>('/api/availability', payload);
    });

    this.bulkSaving.set(true);
    forkJoin(reqs).subscribe({
      next: () => {
        this.bulkSaving.set(false);
        this.bulkOpen.set(false);
        this.selectedDates.set(new Set());
        this.toast.success(`Availability set for ${dates.length} day(s).`);
        this.loadEntries(propertyId);
      },
      error: () => this.bulkSaving.set(false),
    });
  }

  // ---- day editor ----
  protected openDay(cell: DayCell): void {
    if (this.suppressClick) {
      this.suppressClick = false; // consumed the click that ended a drag
      return;
    }
    // Guest booking: pick an available date to fill the booking form (a
    // secondary way to choose dates, alongside typing them in). Doesn't open
    // the editor and only fires when the caller opts in via [selectable].
    if (this.selectable()) {
      // A guest can only pick today or a future night, however the calendar was
      // reached — a stay can't start in the past.
      if (cell.entry?.availabilityStatus === 'AVAILABLE' && !this.isPast(cell.date)) {
        this.dateSelected.emit(cell.date);
      }
      return;
    }
    if (this.readOnly() || !this.selectedId()) {
      return;
    }
    if (this.isPast(cell.date)) {
      this.toast.info('Past days can’t be changed — start from today.');
      return;
    }
    this.editingDate.set(cell.date);
    this.editingEntryId.set(cell.entry?.id ?? null);
    this.selectedStatus.set(cell.entry?.availabilityStatus ?? 'AVAILABLE');
    this.form = this.buildForm(cell.entry);
    this.editorOpen.set(true);
  }

  protected closeEditor(): void {
    this.editorOpen.set(false);
  }

  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
  }

  // Only basePrice lives in the reactive form; status is a signal.
  private buildForm(entry?: AvailabilityCalendarResponse): FormGroup {
    return this.fb.group({
      basePrice: [entry?.basePrice ?? '', [Validators.required, Validators.min(0.01)]],
    });
  }

  protected saveDay(): void {
    const propertyId = this.selectedId();
    if (this.form.invalid || !propertyId) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isPast(this.editingDate())) {
      this.toast.error('Past days can’t be changed — start from today.');
      this.editorOpen.set(false);
      return;
    }
    const raw = this.form.getRawValue() as { basePrice: number };
    const payload = {
      propertyId,
      calendarDate: this.editingDate(),
      availabilityStatus: this.selectedStatus() as AvailabilityStatus,
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
    const past = this.isPast(cell.date) ? ' se-cal-past' : '';
    if (!cell.entry) {
      return 'se-cal-unset' + past;
    }
    return 'se-cal-' + cell.entry.availabilityStatus.toLowerCase() + past;
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  private pad(n: number): string {
    return String(n).padStart(2, '0');
  }
}
