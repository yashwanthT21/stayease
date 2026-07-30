import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { AuthService } from '../../core/auth/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { GuestProfileResponse } from '../../core/models/dtos';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';

/**
 * Lets a guest create/edit their own guest profile (name, email, phone,
 * nationality). Verification, status, review score and booking count are
 * server-managed and shown read-only. The profile is looked up by the
 * signed-in user's id; a first save creates it, later saves update it.
 */
@Component({
  selector: 'app-guest-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe],
  templateUrl: './guest-profile.html',
})
export class GuestProfileComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly profile = signal<GuestProfileResponse | null>(null);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    phone: ['', [Validators.maxLength(20)]],
    nationality: ['', [Validators.maxLength(80)]],
  });

  constructor() {
    const userId = this.auth.user()?.userId ?? 0;
    this.crud.list<GuestProfileResponse>('/api/guests', { userId }).subscribe({
      next: (rows) => {
        const mine = rows.find((r) => r.userId === userId) ?? rows[0] ?? null;
        this.profile.set(mine);
        this.form.patchValue({
          name: mine?.name ?? '',
          email: mine?.email ?? this.auth.user()?.email ?? '',
          phone: mine?.phone ?? '',
          nationality: mine?.nationality ?? '',
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const userId = this.auth.user()?.userId ?? 0;
    const value = this.form.getRawValue();
    const payload: Record<string, unknown> = { userId, name: value.name, email: value.email };
    if (value.phone) {
      payload['phone'] = value.phone;
    }
    if (value.nationality) {
      payload['nationality'] = value.nationality;
    }

    this.saving.set(true);
    const existing = this.profile();
    const req$ = existing
      ? this.crud.update<GuestProfileResponse>('/api/guests', existing.id, payload)
      : this.crud.create<GuestProfileResponse>('/api/guests', payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.profile.set(saved);
        this.toast.success('Profile saved.');
      },
      error: () => this.saving.set(false),
    });
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected verificationBadge(status: string | undefined): string {
    switch (status) {
      case 'TRUSTED':
      case 'ID_VERIFIED':
        return 'text-bg-success';
      default:
        return 'text-bg-secondary';
    }
  }
}
