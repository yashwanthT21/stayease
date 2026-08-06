import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { PropertyResponse } from '../../core/models/dtos';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { AvailabilityCalendarComponent } from './availability-calendar';
import { BookingInput, BookingService, NotAvailableError } from '../booking/booking.service';

/**
 * The guest's Properties tab: a gallery of bookable (LISTED) properties. Picking
 * one opens a detail view with a READ-ONLY availability calendar plus a booking
 * form (check-in, check-out, guests). Submitting creates a PENDING request for a
 * manager to approve.
 *
 * A stay can't start in the past: the date inputs carry a `min` of today, the
 * calendar won't hand back a past day, and requestBooking() rejects one anyway in
 * case a date was typed straight into the field.
 *
 * The party size is checked against the chosen property's own maxGuests, so a
 * guest is told "sleeps 4" while they're typing rather than after the request has
 * been sent and a manager has had to reject it.
 */
@Component({
  selector: 'app-guest-browse',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, AvailabilityCalendarComponent],
  templateUrl: './browse-properties.html',
})
export class BrowsePropertiesComponent {
  private fb = inject(FormBuilder);
  private booking = inject(BookingService);
  private toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly triedSubmit = signal(false);
  protected readonly properties = signal<PropertyResponse[]>([]);
  protected readonly selected = signal<PropertyResponse | null>(null);

  /** Free-text search over the gallery: matches a property's name or its city. */
  protected readonly search = signal('');

  /**
   * The gallery, narrowed by the search box. Name and city are the two things a
   * guest actually knows before they know anything else about a listing ("that
   * villa in Kochi"), so one field searches both rather than making them choose
   * which they meant.
   */
  protected readonly visibleProperties = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return this.properties();
    }
    return this.properties().filter(
      (p) =>
        (p.title ?? '').toLowerCase().includes(term) || (p.city ?? '').toLowerCase().includes(term),
    );
  });

  /** How many people the picked property sleeps; null before one is picked. */
  protected readonly maxGuests = computed(() => this.selected()?.maxGuests ?? null);

  protected readonly form = this.fb.group({
    checkInDate: ['', [Validators.required]],
    checkOutDate: ['', [Validators.required]],
    // The upper bound is the property's own capacity, so it has to be a closure
    // over the current selection rather than a fixed Validators.max().
    guestCount: [1, [Validators.required, Validators.min(1), this.maxGuestsValidator()]],
  });

  /** True once the party is bigger than the property allows. */
  protected readonly tooManyGuests = computed(() => !!this.form.get('guestCount')?.errors?.['overCapacity']);

  constructor() {
    this.booking.listedProperties().subscribe({
      next: (rows) => {
        this.properties.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected clearSearch(): void {
    this.search.set('');
  }

  /**
   * Rejects a party larger than the selected property sleeps.
   *
   * Reads the capacity live from `selected()` so switching property re-evaluates
   * the same control — the alternative (rebuilding the validator on every pick)
   * would have to remember to re-run validation by hand.
   */
  private maxGuestsValidator() {
    return (control: AbstractControl): ValidationErrors | null => {
      const max = this.selected()?.maxGuests;
      const value = Number(control.value);
      if (max == null || !Number.isFinite(value) || value <= max) {
        return null;
      }
      return { overCapacity: { max, actual: value } };
    };
  }

  /** Today as yyyy-MM-dd — the earliest night a guest may request. */
  protected today(): string {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  }

  protected pick(property: PropertyResponse): void {
    this.selected.set(property);
    this.triedSubmit.set(false);
    // Set the selection FIRST: reset() re-runs the validators, and the capacity
    // one reads the new property's maxGuests.
    this.form.reset({ guestCount: 1 });
  }

  protected backToList(): void {
    this.selected.set(null);
  }

  /**
   * Secondary date entry: tapping an available day on the calendar fills the
   * booking form. First tap (or a tap on/before the current check-in) sets the
   * check-in and clears check-out; a later tap sets the check-out.
   */
  protected onCalendarDate(date: string): void {
    const checkIn = this.form.get('checkInDate')!.value ?? '';
    const checkOut = this.form.get('checkOutDate')!.value ?? '';
    if (!checkIn || checkOut || date <= checkIn) {
      this.form.patchValue({ checkInDate: date, checkOutDate: '' });
    } else {
      this.form.patchValue({ checkOutDate: date });
    }
  }

  protected typeIcon(type: string): string {
    switch (type) {
      case 'VILLA':
        return 'bi-house-heart';
      case 'APARTMENT':
        return 'bi-building';
      case 'STUDIO':
        return 'bi-door-closed';
      case 'COTTAGE':
        return 'bi-tree';
      case 'TOWNHOUSE':
        return 'bi-buildings';
      default:
        return 'bi-houses';
    }
  }

  protected requestBooking(): void {
    const property = this.selected();
    this.triedSubmit.set(true);
    if (this.form.invalid || !property) {
      this.form.markAllAsTouched();
      // The inline field error is the primary signal, but capacity is the one
      // failure a guest can't diagnose from the form alone (the limit lives on
      // the listing), so it also gets said out loud.
      if (this.tooManyGuests() && property) {
        this.toast.error(
          `${property.title} sleeps up to ${property.maxGuests} guest${property.maxGuests === 1 ? '' : 's'}.`,
        );
      }
      return;
    }
    const value = this.form.getRawValue();
    if ((value.checkOutDate ?? '') <= (value.checkInDate ?? '')) {
      this.toast.error('Check-out must be after check-in.');
      return;
    }
    if ((value.checkInDate ?? '') < this.today()) {
      this.toast.error('Check-in can’t be in the past — pick today or a later date.');
      return;
    }

    const input: BookingInput = {
      propertyId: property.id,
      checkInDate: value.checkInDate!,
      checkOutDate: value.checkOutDate!,
      guestCount: Number(value.guestCount),
    };

    this.submitting.set(true);
    this.booking.book(input).subscribe({
      next: () => {
        this.submitting.set(false);
        this.toast.success('Booking request sent — awaiting manager approval.');
        this.selected.set(null);
      },
      error: (err) => {
        this.submitting.set(false);
        if (err instanceof NotAvailableError) {
          this.toast.error(`Not available on ${err.date}. Please choose available (green) dates.`);
        } else {
          this.toast.error(err?.message || 'Booking request failed. Please try again.');
        }
      },
    });
  }
}
