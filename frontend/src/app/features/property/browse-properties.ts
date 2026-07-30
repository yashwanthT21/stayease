import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
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

  protected readonly form = this.fb.group({
    checkInDate: ['', [Validators.required]],
    checkOutDate: ['', [Validators.required]],
    guestCount: [1, [Validators.required, Validators.min(1)]],
  });

  constructor() {
    this.booking.listedProperties().subscribe({
      next: (rows) => {
        this.properties.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected pick(property: PropertyResponse): void {
    this.selected.set(property);
    this.triedSubmit.set(false);
    this.form.reset({ guestCount: 1 });
  }

  protected backToList(): void {
    this.selected.set(null);
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
      return;
    }
    const value = this.form.getRawValue();
    if ((value.checkOutDate ?? '') <= (value.checkInDate ?? '')) {
      this.toast.error('Check-out must be after check-in.');
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
      next: (reservation) => {
        this.submitting.set(false);
        this.toast.success(`Request sent (reservation #${reservation.id}). Awaiting manager approval.`);
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
