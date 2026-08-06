import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ResourcePageComponent } from './resource-page';
import { ResourceConfig } from '../crud/resource-config';
import { installMemoryLocalStorage } from '../../testing/local-storage';

/**
 * The generic CRUD screen renders most of the app, so the three behaviours it owns
 * are worth pinning down here rather than once per resource:
 *
 *  1. A dropdown choice STICKS. This is the reported bug — "I select x, come out of
 *     the dropdown, submit, and it's back to the default" — and the screen is where
 *     it bit hardest, because reference options arrive from a second HTTP call after
 *     the modal has already rendered. Every test drives the real DOM (set
 *     `<select>.value`, dispatch `change`) and asserts on the request body, because
 *     the bug lived exactly in the gap between the element and the model.
 *  2. An editable per-row dropdown saves the MAPPED value, not the label shown.
 *  3. Long free text gets a way to read the rest of it.
 */
describe('ResourcePageComponent', () => {
  let fixture: ComponentFixture<ResourcePageComponent>;
  let http: HttpTestingController;

  /** A resource with one enum select, one async reference, and a long-text column. */
  const CONFIG: ResourceConfig = {
    key: 'widgets',
    apiBase: '/api/widgets',
    title: 'Widgets',
    singular: 'Widget',
    icon: 'bi-gear',
    group: 'Test',
    listColumns: ['id', 'note', 'kind'],
    fields: [
      { key: 'ownerId', label: 'Owner', type: 'reference', ref: { resourceKey: 'guests', labelFields: ['name'] }, required: true },
      { key: 'kind', label: 'Kind', type: 'select', options: ['ALPHA', 'BETA', 'GAMMA'] },
      { key: 'note', label: 'Note', type: 'text' },
    ],
  };

  /** Same shape, but the Kind column is an editable per-row dropdown. */
  const EDITABLE_CONFIG: ResourceConfig = {
    ...CONFIG,
    fields: [
      { key: 'kind', label: 'Kind', type: 'select', options: ['ALPHA', 'BETA'] },
      { key: 'note', label: 'Note', type: 'text' },
    ],
    listColumns: ['id', 'note', 'kind'],
    rowEditors: [
      {
        key: 'kind',
        options: ['Off', 'On'],
        fromRow: (row) => (row['kind'] === 'BETA' ? 'On' : 'Off'),
        toValue: (option) => (option === 'On' ? 'BETA' : 'ALPHA'),
      },
    ],
  };

  async function start(config: ResourceConfig, rows: unknown[] = []): Promise<void> {
    installMemoryLocalStorage();
    await TestBed.configureTestingModule({
      imports: [ResourcePageComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { config } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResourcePageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/widgets' && r.method === 'GET').flush(rows);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  /** Answer the reference-options call — deliberately AFTER the modal is open. */
  async function resolveGuests(): Promise<void> {
    for (const req of http.match((r) => r.url === '/api/guests' && r.method === 'GET')) {
      req.flush([
        { id: 42, userId: 100, name: 'Asha Nair', email: 'asha@example.com', status: 'ACTIVE' },
        { id: 43, userId: 101, name: 'Ravi Kumar', email: 'ravi@example.com', status: 'ACTIVE' },
      ]);
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function el<T extends HTMLElement>(selector: string): T {
    const found = fixture.nativeElement.querySelector(selector) as T | null;
    if (!found) {
      throw new Error(`expected to find "${selector}" in the rendered screen`);
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

  afterEach(() => {
    http?.verify();
  });

  it('keeps an enum choice when the modal re-renders', async () => {
    await start(CONFIG);
    await resolveGuests();
    await click(el<HTMLButtonElement>('.btn-primary'));

    const [ownerSelect, kindSelect] = modalSelects();
    await pick(ownerSelect, '42');
    await pick(kindSelect, 'GAMMA');

    // A re-render used to be enough to snap the element back to option 0.
    fixture.detectChanges();
    await fixture.whenStable();
    expect(kindSelect.value).toBe('GAMMA');

    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const post = http.expectOne((r) => r.url === '/api/widgets' && r.method === 'POST');
    expect(post.request.body).toMatchObject({ ownerId: 42, kind: 'GAMMA' });
    post.flush({ id: 1, ...(post.request.body as object) });
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/widgets' && r.method === 'GET').flush([]);
    await fixture.whenStable();
  });

  it('keeps a reference choice made BEFORE the options list arrives', async () => {
    // The original failure mode: options render late, the browser falls back to the
    // placeholder, and the user's pick is silently lost.
    await start(CONFIG);
    await click(el<HTMLButtonElement>('.btn-primary'));
    await resolveGuests();

    const [ownerSelect, kindSelect] = modalSelects();
    await pick(ownerSelect, '43');
    await pick(kindSelect, 'BETA');
    expect(ownerSelect.value).toBe('43');

    el<HTMLFormElement>('.modal form').dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    const post = http.expectOne((r) => r.url === '/api/widgets' && r.method === 'POST');
    expect(post.request.body).toMatchObject({ ownerId: 43, kind: 'BETA' });
    post.flush({ id: 2, ...(post.request.body as object) });
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/widgets' && r.method === 'GET').flush([]);
    await fixture.whenStable();
  });

  it('shows the row editor with the row\'s current value and saves the mapped one', async () => {
    await start(EDITABLE_CONFIG, [{ id: 5, kind: 'ALPHA', note: 'a widget' }]);

    const rowSelect = el<HTMLSelectElement>('tbody select.se-row-editor');
    // ALPHA maps to the "Off" label, and the header is a plain label (no filter).
    expect(rowSelect.value).toBe('Off');
    expect(Array.from(rowSelect.options).map((o) => o.value)).toEqual(['Off', 'On']);
    expect(fixture.nativeElement.querySelector('thead select')).toBeNull();

    await pick(rowSelect, 'On');

    // Saves the STORED value the label maps to, not the label itself.
    const put = http.expectOne((r) => r.url === '/api/widgets/5' && r.method === 'PUT');
    expect(put.request.body).toMatchObject({ kind: 'BETA', note: 'a widget' });
    put.flush({ id: 5, kind: 'BETA', note: 'a widget' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el<HTMLSelectElement>('tbody select.se-row-editor').value).toBe('On');
  });

  it('offers a way to read a long value the table has to ellipsise', async () => {
    const long = 'New booking request for Sea Breeze Villa (Kochi): Asha Nair, 2027-01-10 to 2027-01-14 — approve or reject it from your Reservations screen.';
    await start(CONFIG, [{ id: 9, note: long, kind: 'ALPHA' }]);
    await resolveGuests();

    // Truncated in the cell, so there must be an escape hatch.
    expect(fixture.nativeElement.querySelector('td .se-truncate')).not.toBeNull();
    await click(el<HTMLButtonElement>('td button.btn-link'));

    // The dialog carries the WHOLE value, wrapped rather than clipped.
    const body = el<HTMLElement>('.modal .se-wrap-text');
    expect(body.textContent).toContain(long);
  });

  it('shows a short value in full, with no read-more button', async () => {
    await start(CONFIG, [{ id: 9, note: 'short', kind: 'ALPHA' }]);
    await resolveGuests();

    expect(fixture.nativeElement.querySelector('td .se-truncate')).toBeNull();
    expect(fixture.nativeElement.querySelector('td button.btn-link')).toBeNull();
  });
});
