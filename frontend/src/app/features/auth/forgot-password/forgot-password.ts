import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { PasswordResetService } from '../../../core/services/password-reset';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: './forgot-password.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPassword {
  private readonly passwordResetService = inject(PasswordResetService);
  private readonly cdr = inject(ChangeDetectorRef);

  email = '';
  message = '';
  error = '';
  loading = false;

  requestReset(): void {
    this.message = '';
    this.error = '';

    const email = this.email.trim();

    if (!email) {
      this.error = 'Please enter your registered email address.';
      this.cdr.markForCheck();
      return;
    }

    this.loading = true;
    this.cdr.markForCheck();

    this.passwordResetService
      .requestReset(email)
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.markForCheck();
        }),
      )
      .subscribe({
        next: (response) => {
          this.message = response.message;
          this.cdr.markForCheck();
        },
        error: (err: unknown) => {
          console.error('Forgot-password request failed:', err);
          this.error =
            'Unable to process the request. Please try again later.';
          this.cdr.markForCheck();
        },
      });
  }
}
