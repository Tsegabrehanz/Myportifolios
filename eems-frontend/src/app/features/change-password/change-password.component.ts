import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PasswordChangeApiService } from '../../core/services/password-change-api.service';

type Step = 'details' | 'verify' | 'done';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})
export class ChangePasswordComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PasswordChangeApiService);
  private readonly route = inject(ActivatedRoute);

  readonly step = signal<Step>('details');
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly maskedPhoneNumber = signal<string | null>(null);
  readonly isMandatory = signal(this.route.snapshot.queryParamMap.get('reason') === 'temporary');
  readonly resendCooldownSeconds = signal(0);
  private resendTimer?: ReturnType<typeof setInterval>;
  private static readonly RESEND_COOLDOWN = 30;

  readonly detailsForm = this.fb.group({
    phoneNumber: [''], // optional - only needed if not already on file, or to update it
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(8)]],
    confirmNewPassword: ['', Validators.required]
  });

  readonly otpForm = this.fb.group({
    otpCode: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  submitDetails(): void {
    if (this.detailsForm.invalid) {
      return;
    }
    const { phoneNumber, currentPassword, newPassword, confirmNewPassword } = this.detailsForm.getRawValue();

    if (newPassword !== confirmNewPassword) {
      this.errorMessage.set('New password and confirmation do not match.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);

    const proceedToInitiate = () => {
      this.api.initiate({ currentPassword: currentPassword!, newPassword: newPassword! }).subscribe({
        next: (response) => {
          this.loading.set(false);
          this.maskedPhoneNumber.set(response.maskedPhoneNumber);
          this.step.set('verify');
          this.startResendCooldown();
        },
        error: (err) => {
          this.loading.set(false);
          this.errorMessage.set(this.extractError(err, 'Could not start password change.'));
        }
      });
    };

    if (phoneNumber && phoneNumber.trim().length > 0) {
      this.api.updatePhoneNumber({ phoneNumber: phoneNumber.trim() }).subscribe({
        next: proceedToInitiate,
        error: (err) => {
          this.loading.set(false);
          this.errorMessage.set(this.extractError(err, 'Could not update phone number.'));
        }
      });
    } else {
      proceedToInitiate();
    }
  }

  submitOtp(): void {
    if (this.otpForm.invalid) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);

    const { otpCode } = this.otpForm.getRawValue();
    this.api.confirm({ otpCode: otpCode! }).subscribe({
      next: () => {
        this.loading.set(false);
        this.step.set('done');
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractError(err, 'Verification failed.'));
      }
    });
  }

  startOver(): void {
    this.step.set('details');
    this.errorMessage.set(null);
    this.otpForm.reset();
    this.detailsForm.reset();
    this.clearResendTimer();
    this.resendCooldownSeconds.set(0);
  }

  /**
   * Re-calls initiate with the same current/new password already
   * entered in step 1 (detailsForm isn't reset when moving to 'verify',
   * so those values are still there). The backend always looks up the
   * *most recent* unconsumed request for the user, so an old
   * still-pending one from the previous call is simply superseded -
   * no explicit invalidation needed.
   */
  resendCode(): void {
    if (this.resendCooldownSeconds() > 0 || this.loading()) {
      return;
    }
    const { currentPassword, newPassword } = this.detailsForm.getRawValue();
    if (!currentPassword || !newPassword) {
      // Shouldn't normally happen (you can't reach 'verify' without them), but
      // if it does, sending the user back to re-enter is safer than guessing.
      this.startOver();
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.api.initiate({ currentPassword, newPassword }).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.maskedPhoneNumber.set(response.maskedPhoneNumber);
        this.startResendCooldown();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractError(err, 'Could not resend the code.'));
      }
    });
  }

  ngOnDestroy(): void {
    this.clearResendTimer();
  }

  private startResendCooldown(): void {
    this.clearResendTimer();
    this.resendCooldownSeconds.set(ChangePasswordComponent.RESEND_COOLDOWN);
    this.resendTimer = setInterval(() => {
      const remaining = this.resendCooldownSeconds() - 1;
      if (remaining <= 0) {
        this.resendCooldownSeconds.set(0);
        this.clearResendTimer();
      } else {
        this.resendCooldownSeconds.set(remaining);
      }
    }, 1000);
  }

  private clearResendTimer(): void {
    if (this.resendTimer) {
      clearInterval(this.resendTimer);
      this.resendTimer = undefined;
    }
  }

  private extractError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || fallback;
  }
}
