import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UsersApi } from '../../core/api/users.api';
import { AuthStore } from '../../core/auth/auth.store';
import type { Role, UserProfile } from '../../core/models/api.models';
import { formatDateTime } from '../../shared/format';
import { ToastService } from '../../shared/toast.service';

@Component({
  selector: 'app-profile',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.html',
})
export class ProfileComponent {
  private readonly api = inject(UsersApi);
  private readonly fb = inject(FormBuilder);
  private readonly toasts = inject(ToastService);
  protected readonly auth = inject(AuthStore);

  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly people = signal<UserProfile[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly savingProfile = signal(false);
  protected readonly savingPassword = signal(false);

  protected readonly roles: Role[] = ['ADMIN', 'MANAGER', 'EMPLOYEE'];
  protected readonly formatDateTime = formatDateTime;

  protected readonly profileForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
  });

  protected readonly passwordForm = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(72)]],
  });

  constructor() {
    this.api.me().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.profileForm.patchValue({ name: profile.name });
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.toasts.error(err.message);
        this.loading.set(false);
      },
    });

    // Admin-only. The list endpoint is also admin-only server side, so a
    // non-admin reaching this branch would simply get a 403.
    if (this.auth.isAdmin()) {
      this.loadPeople();
    }
  }

  private loadPeople(): void {
    this.api.list(null).subscribe({
      next: (page) => this.people.set(page.content),
      error: (err: Error) => this.toasts.error(err.message),
    });
  }

  protected saveProfile(): void {
    if (this.profileForm.invalid || this.savingProfile()) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile.set(true);
    const name = this.profileForm.getRawValue().name.trim();

    this.api.updateProfile(name).subscribe({
      next: (profile) => {
        this.profile.set(profile);
        // Keep the header's cached name in step with the saved one.
        this.auth.patchName(profile.name);
        this.toasts.success('Profile updated');
        this.savingProfile.set(false);
      },
      error: (err: Error) => {
        this.toasts.error(err.message);
        this.savingProfile.set(false);
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.savingPassword()) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword.set(true);
    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    this.api.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.passwordForm.reset({ currentPassword: '', newPassword: '' });
        this.toasts.success('Password changed');
        this.savingPassword.set(false);
      },
      error: (err: Error) => {
        this.toasts.error(err.message);
        this.savingPassword.set(false);
      },
    });
  }

  protected changeRole(person: UserProfile, role: string): void {
    this.api.changeRole(person.id, role as Role).subscribe({
      next: () => {
        this.toasts.success(`${person.name} is now ${role}`);
        this.loadPeople();
      },
      // Refuses when it would remove the last administrator, and says why.
      error: (err: Error) => {
        this.toasts.error(err.message);
        this.loadPeople();
      },
    });
  }

  protected toggleEnabled(person: UserProfile): void {
    this.api.setEnabled(person.id, !person.enabled).subscribe({
      next: () => {
        this.toasts.success(`${person.name} ${person.enabled ? 'disabled' : 'enabled'}`);
        this.loadPeople();
      },
      error: (err: Error) => this.toasts.error(err.message),
    });
  }
}
