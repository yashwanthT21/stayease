import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { RegisterRequest } from '../../core/auth/auth.models';
import { USER_ROLES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';

@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LabelizePipe],
  templateUrl: './register.html',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  protected readonly roles = USER_ROLES;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  // Role is captured via a plain (change) handler into a signal rather than a
  // reactive-forms select control: in this zoneless app the reactive select
  // accessor can miss the FIRST selection (needing a second click). A native
  // <select> with a static-selected placeholder + (change) captures reliably.
  protected readonly selectedRole = signal('');
  protected readonly triedSubmit = signal(false);

  protected form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(180)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
    phone: ['', [Validators.maxLength(20)]],
  });

  onRoleChange(event: Event): void {
    this.selectedRole.set((event.target as HTMLSelectElement).value);
  }

  submit(): void {
    this.triedSubmit.set(true);
    if (this.form.invalid || !this.selectedRole()) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const payload = { ...this.form.getRawValue(), role: this.selectedRole() } as RegisterRequest;
    this.auth.register(payload).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.status === 409 ? 'An account with that email already exists.' : 'Registration failed. Please check your details.');
      },
    });
  }
}
