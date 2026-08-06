import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CheckOutComponent } from './check-out';
import { installMemoryLocalStorage } from '../../testing/local-storage';

/**
 * Companion to check-in.spec.ts: the same "my selection reverted to the default"
 * cover for check-outs, plus the two things the deposit-release removal must not
 * break — the field is gone from the UI, and an edit still round-trips whatever
 * value finance already set.
 */
describe('CheckOutComponent — form field capture', () => {
  let fixture: ComponentFixture<CheckOutComponent>;
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
    status: 'CHECKED_OUT',
  };
  const EXISTING_RECORD = {
    id: 11,
    reservationId: 7,
    actualCheckOut: '2026-09-04T10:30:00',
    damageNoted: false,
    depositReleased: true,
    status: 'CHECKED_OUT',
  };

  async function bootstrapScreen(records: unknown[]): Promise<void> {
    http.expectOne((r) => r.url === '/api/check-outs' && r.method === 'GET').flush(records);
    http.expectOne((r) => r.url === '/api/reservations' && r.method === 'GET').flush([RESERVATION]);
    http.expectOne((r) => r.url === '/api/guests' && r.method === 'GET').flush([
      { id: 42, userId: 100, name: 'Asha Nair', email: 'asha@example.com', verificationStatus: 'ID_VERIFIED', bookingCount: 1, status: 'ACTIVE' },
    ]);
    for (const req of http.match((r) => r.url.startsWith('/api/properties/'))) {
      req.flush({ id: 3, ownerId: 1, title: 'Sea Breeze Villa', type: 'VILLA', city: 'Kochi', maxGuests: 4, bedrooms: 2, bathrooms: 2, status: 'LISTED' });
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function el<T extends HTMLElement>(selector: string): T {
    const found = fixture.nativeElement.querySelector(selector) as T | null;
    if (!found) {
      throw new Error(`expected to find "${selector}" in the rendered check-out screen`);
    }
    return found;
  }

  function modalSelects(): HTMLSelectElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.modal select')) as HTMLSelectElement[];
  }

  async function pick(select: HTMLSelectElement, value: string): Promise<void> {
    select.value = value;
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function click(element: HTMLElement): Promise<void> {
    element.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function submitModal(): Promise<void> {
    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  async function start(records: unknown[] = []): Promise<void> {
    installMemoryLocalStorage();
    await TestBed.configureTestingModule({
      imports: [CheckOutComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(CheckOutComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bootstrapScreen(records);
  }

  afterEach(() => {
    http?.verify();
  });

  it('posts the chosen reservation rather than the default', async () => {
    await start();
    await click(el<HTMLButtonElement>('app-owner-page-header button'));
    const [reservationSelect] = modalSelects();

    // The reservation list resolves asynchronously, which is what used to make a
    // pick revert to the placeholder while the signal held the real choice.
    await pick(reservationSelect, '7');

    await submitModal();

    const post = http.expectOne((r) => r.url === '/api/check-outs' && r.method === 'POST');
    expect(post.request.body).toMatchObject({
      reservationId: 7,
      status: 'CHECKED_OUT',
      damageNoted: false,
      depositReleased: false,
    });
    post.flush({ id: 1, ...(post.request.body as object) });
    await fixture.whenStable();
  });

  it('offers CHECKED_OUT as the only status — damage is the flag, not a status', async () => {
    await start();
    await click(el<HTMLButtonElement>('app-owner-page-header button'));
    const [, statusSelect] = modalSelects();

    // DAMAGE_REPORTED used to be selectable here, which let one record disagree
    // with itself (status DAMAGE_REPORTED while damageNoted was false). Recording
    // a departure now has exactly one outcome; damage rides on `damageNoted`.
    const offered = Array.from(statusSelect.options).map((o) => o.value);
    expect(offered).toEqual(['CHECKED_OUT']);
    expect(offered).not.toContain('DAMAGE_REPORTED');
    expect(fixture.nativeElement.querySelector('.modal #damageNoted')).not.toBeNull();
  });

  it('no longer offers a deposit-released field', async () => {
    await start([EXISTING_RECORD]);

    expect(fixture.nativeElement.querySelector('#depositReleased')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Deposit released');

    await click(el<HTMLButtonElement>('app-owner-page-header button'));
    expect(fixture.nativeElement.querySelector('.modal #depositReleased')).toBeNull();
  });

  it('preserves an already-released deposit when a record is edited', async () => {
    await start([EXISTING_RECORD]);

    await click(el<HTMLButtonElement>('table .btn-outline-secondary'));
    // Flip the one damage field the screen does own, so the PUT is a real edit.
    const damageNoted = el<HTMLInputElement>('.modal #damageNoted');
    damageNoted.click();
    await fixture.whenStable();
    fixture.detectChanges();

    await submitModal();

    const put = http.expectOne((r) => r.url === '/api/check-outs/11' && r.method === 'PUT');
    // depositReleased isn't on this form, but a PUT replaces the whole record —
    // so the stored value has to be carried through rather than reset to false.
    expect(put.request.body).toMatchObject({ damageNoted: true, depositReleased: true });
    put.flush({ ...EXISTING_RECORD, damageNoted: true });
    await fixture.whenStable();
  });

  it('does not submit without a reservation', async () => {
    await start();
    await click(el<HTMLButtonElement>('app-owner-page-header button'));

    await submitModal();

    http.expectNone((r) => r.url === '/api/check-outs' && r.method === 'POST');
  });
});
