import { Directive, ElementRef, EventEmitter, HostListener, Input, Output, inject } from '@angular/core';

/**
 * Keeps a native `<select>`'s DOM value and its model value in lock-step.
 *
 * The screens here drive selects from signals rather than `formControlName`, and
 * the first attempt at that marked the chosen option with `[selected]` on each
 * `<option>`. That is where "my selection reverted to the default" came from: an
 * `[selected]` binding is only re-applied when its own expression changes, so any
 * time Angular re-created the option list — a reference list resolving after the
 * modal opened, `@for` re-rendering, a value written programmatically (the guest
 * auto-filled from a reservation) — the browser fell back to the first option
 * while the model still held the real choice, or vice versa.
 *
 * This directive instead:
 *  - writes the model value onto the element after every change detection pass,
 *    so freshly-rendered options immediately show the right selection;
 *  - only writes a value the element can actually hold, so it never fights the
 *    browser into option 0 while an async list is still loading;
 *  - never writes when the DOM already agrees, so an open dropdown is left alone;
 *  - reports the user's pick straight from the element on `change`.
 *
 * Usage: `<select [seSelectValue]="statusSignal()" (seSelectValueChange)="statusSignal.set($event)">`
 * with plain `<option [value]="…">` children (no `[selected]` bindings).
 */
@Directive({ selector: 'select[seSelectValue]' })
export class SelectValueDirective {
  private readonly el = inject<ElementRef<HTMLSelectElement>>(ElementRef);

  /** '' selects the placeholder option; null/undefined are treated as ''. */
  @Input() set seSelectValue(value: string | number | null | undefined) {
    this.wanted = value === null || value === undefined ? '' : String(value);
    this.sync();
  }

  /** The element's value after the user picked an option. */
  @Output() readonly seSelectValueChange = new EventEmitter<string>();

  private wanted = '';

  @HostListener('change')
  protected onChange(): void {
    this.wanted = this.el.nativeElement.value;
    this.seSelectValueChange.emit(this.wanted);
  }

  // Both hooks run after the surrounding view (and therefore the @for-rendered
  // options) has been processed. Whichever Angular calls, the element is synced.
  ngAfterContentChecked(): void {
    this.sync();
  }

  ngAfterViewChecked(): void {
    this.sync();
  }

  private sync(): void {
    const el = this.el.nativeElement;
    if (el.value === this.wanted) {
      return; // already correct — don't disturb an open dropdown
    }
    if (Array.from(el.options).some((o) => o.value === this.wanted)) {
      el.value = this.wanted;
    }
  }
}
