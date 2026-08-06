import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CheckInComponent } from './check-in';
import { installMemoryLocalStorage } from '../../testing/local-storage';

/**
 * Regression cover for the reported bug: "when I click Create check-in, my
 * selected fields sometimes aren't set and it defaults to the default value."
 *
 * Every test drives the real DOM (set `<select>.value`, dispatch `change`) rather
 * than poking component internals, because the bug lived exactly in that gap
 * between the element and the model. The assertion is always on the POST body —
 * what the backend would actually have stored.
 */
describe('CheckInComponent — form field capture', () => {
  let fixture: ComponentFixture<CheckInComponent>;
  let http: HttpTestingController;

  const RESERVATION = {
    id: 7,
    propertyId: 3,
    guestId: 42,
    checkInDate: '2026-09-01',
    checkOutDate: '2026-09-04',
    nights: 3,
    guestCount: 2,
    baseAmount: 9000,
    totalAmount: 9000,
    bookingSource: 'PLATFORM',
    status: 'CONFIRMED',
  };
  const OTHER_RESERVATION = { ...RESERVATION, id: 8, guestId: 43, propertyId: 3 };
  const GUESTS = [
    { id: 42, userId: 100, name: 'Asha Nair', email: 'asha@example.com', verificationStatus: 'ID_VERIFIED', bookingCount: 1, status: 'ACTIVE' },
    { id: 43, userId: 101, name: 'Ravi Kumar', email: 'ravi@example.com', verificationStatus: 'UNVERIFIED', bookingCount: 0, status: 'ACTIVE' },
  ];

  /** Answer the screen's start-up requests so the pickers are populated. */
  async function bootstrapScreen(): Promise<void> {
    http.expectOne((r) => r.url === '/api/check-ins' && r.method === 'GET').flush([]);
    http.expectOne((r) => r.url === '/api/reservations' && r.method === 'GET').flush([RESERVATION, OTHER_RESERVATION]);
    http.expectOne((r) => r.url === '/api/guests' && r.method === 'GET').flush(GUESTS);
    // Property titles are resolved one id at a time for the reservation labels.
    for (const req of http.match((r) => r.url.startsWith('/api/properties/'))) {
      req.flush({ id: 3, ownerId: 1, title: 'Sea Breeze Villa', type: 'VILLA', city: 'Kochi', maxGuests: 4, bedrooms: 2, bathrooms: 2, status: 'LISTED' });
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function el<T extends HTMLElement>(selector: string): T {
    const found = fixture.nativeElement.querySelector(selector) as T | null;
    if (!found) {
      throw new Error(`expected to find "${selector}" in the rendered check-in screen`);
    }
    return found;
  }

  /** All selects inside the open modal, in document order. */
  function modalSelects(): HTMLSelectElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.modal select')) as HTMLSelectElement[];
  }

  async function pick(select: HTMLSelectElement, value: string): Promise<void> {
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function openCreateModal(): Promise<void> {
    el<HTMLButtonElement>('app-owner-page-header button').click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(async () => {
    installMemoryLocalStorage();
    await TestBed.configureTestingModule({
      imports: [CheckInComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CheckInComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bootstrapScreen();
  });

  afterEach(() => {
    http?.verify();
  });

  it('auto-populates the guest from the chosen reservation', async () => {
    await openCreateModal();
    const [reservationSelect, guestSelect] = modalSelects();

    await pick(reservationSelect, '7');

    expect(guestSelect.value).toBe('42');
  });

  it('re-populates the guest when a different reservation is chosen', async () => {
    await openCreateModal();
    const [reservationSelect, guestSelect] = modalSelects();

    await pick(reservationSelect, '7');
    await pick(reservationSelect, '8');

    expect(guestSelect.value).toBe('43');
  });

  it('posts the selected status and access method instead of their defaults', async () => {
    await openCreateModal();
    const [reservationSelect, , accessSelect, statusSelect] = modalSelects();

    await pick(reservationSelect, '7');
    await pick(accessSelect, 'SMART_LOCK');
    await pick(statusSelect, 'CHECKED_IN');

    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const post = http.expectOne((r) => r.url === '/api/check-ins' && r.method === 'POST');
    expect(post.request.body).toMatchObject({
      reservationId: 7,
      guestId: 42,
      accessMethod: 'SMART_LOCK',
      status: 'CHECKED_IN',
      welcomePackSent: false,
    });
    post.flush({ id: 1, ...(post.request.body as object) });
    await fixture.whenStable();
  });

  it('keeps a chosen status when the modal is re-rendered before submitting', async () => {
    await openCreateModal();
    const [reservationSelect, , , statusSelect] = modalSelects();

    await pick(reservationSelect, '7');
    await pick(statusSelect, 'NO_SHOW');

    // Something unrelated re-renders the view (a list refresh, a toast, an async
    // reference list resolving). The selection must survive it.
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(statusSelect.value).toBe('NO_SHOW');

    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const post = http.expectOne((r) => r.url === '/api/check-ins' && r.method === 'POST');
    expect((post.request.body as Record<string, unknown>)['status']).toBe('NO_SHOW');
    post.flush({ id: 2, ...(post.request.body as object) });
    await fixture.whenStable();
  });

  it('does not submit without a reservation', async () => {
    await openCreateModal();

    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone((r) => r.url === '/api/check-ins' && r.method === 'POST');
  });
});
