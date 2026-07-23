import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AuthService } from '../../core/services/auth.service';
import { BrandingService } from '../../core/services/branding.service';
import { ChartComponent } from '../../shared/chart/chart.component';

interface FeatureHighlight {
  icon: string;
  title: string;
  description: string;
}

interface ThemeAccent {
  name: string;
  start: string;
  end: string;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    ChartComponent
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  public readonly brandingService = inject(BrandingService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly showPassword = signal(false);
  readonly capsLockOn = signal(false);
  readonly shakeError = signal(false);

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  // Purely decorative preview data for the hero panel - not a live fetch,
  // since nothing here is authenticated yet. Roughly matches the shape of
  // the real Headcount-by-Department chart on the Analytics page, just to
  // give an honest preview of what's behind the login rather than an
  // arbitrary unrelated graphic.
  readonly previewLabels = ['Engineering', 'Sales', 'Support', 'Marketing', 'Finance'];
  readonly previewData = [42, 28, 19, 15, 11];

  readonly highlights: FeatureHighlight[] = [
    { icon: 'insights', title: 'HR Analytics', description: 'Headcount, tenure, and attrition at a glance — export to PDF or Excel in one click.' },
    { icon: 'event_available', title: 'Leave Management', description: 'Submit and approve leave requests with a manager sign-off workflow built in.' },
    { icon: 'lock_reset', title: 'Secure by Design', description: 'SMS-confirmed password changes and role-based access down to the record level.' },
    { icon: 'upload_file', title: 'Bulk Import', description: 'Onboard hundreds of employees at once from a CSV or Excel file.' }
  ];

  readonly activeHighlight = signal(0);
  private rotationTimer?: ReturnType<typeof setInterval>;

  readonly greeting = signal(this.computeGreeting());
  readonly currentYear = new Date().getFullYear();

  // --- Header: live clock ---
  readonly currentTime = signal(this.formatTime(new Date()));
  private clockTimer?: ReturnType<typeof setInterval>;

  // --- Header: theme accent picker (changes the hero panel's gradient
  // only - deliberately not a full dark-mode toggle, since properly
  // re-theming Angular Material's form fields for dark mode is a bigger
  // job than a header widget should attempt; a broken half-dark form
  // would be worse than not offering the toggle at all). Persisted so
  // the choice survives a refresh. ---
  readonly accents: ThemeAccent[] = [
    { name: 'Blue', start: '#0d47a1', end: '#1e88e5' },
    { name: 'Violet', start: '#4a148c', end: '#7e57c2' },
    { name: 'Teal', start: '#00695c', end: '#26a69a' },
    { name: 'Slate', start: '#263238', end: '#546e7a' }
  ];
  readonly selectedAccent = signal(this.loadSavedAccent());

  // --- Footer: decorative status indicator. Not a real health check -
  // this app has no uptime/status API - it's a static demo affordance,
  // documented here so future-me doesn't mistake it for live monitoring. ---
  readonly statusCheckedAt = signal(this.formatTime(new Date()));

  ngOnInit(): void {
    this.brandingService.loadOnce();

    this.rotationTimer = setInterval(() => {
      this.activeHighlight.update((i) => (i + 1) % this.highlights.length);
    }, 4000);

    this.clockTimer = setInterval(() => {
      this.currentTime.set(this.formatTime(new Date()));
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.rotationTimer) {
      clearInterval(this.rotationTimer);
    }
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
  }

  togglePasswordVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  /** Bound to (keydown)/(keyup) on the password field - real usability
   *  win, especially since the field defaults to masked. */
  onPasswordKeyEvent(event: KeyboardEvent): void {
    if (typeof event.getModifierState === 'function') {
      this.capsLockOn.set(event.getModifierState('CapsLock'));
    }
  }

  selectHighlight(index: number): void {
    this.activeHighlight.set(index);
  }

  selectAccent(accent: ThemeAccent): void {
    this.selectedAccent.set(accent);
    localStorage.setItem('eems_login_accent', accent.name);
  }

  showFooterInfo(label: string): void {
    const messages: Record<string, string> = {
      Privacy: "This is a demo app — there's no real privacy policy to show yet.",
      Terms: "This is a demo app — there's no real terms of service to show yet.",
      Support: 'For help with this project, check the README in the repo.'
    };
    this.snackBar.open(messages[label] ?? label, 'Dismiss', { duration: 4000 });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();
    this.authService.login({ email: email!, password: password! }).subscribe({
      next: (response) => {
        this.loading.set(false);
        if (response.mustChangePassword) {
          this.router.navigate(['/change-password'], { queryParams: { reason: 'temporary' } });
        } else {
          this.router.navigate(['/dashboard']);
        }
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Invalid email or password.');
        this.triggerShake();
      }
    });
  }

  private triggerShake(): void {
    this.shakeError.set(true);
    setTimeout(() => this.shakeError.set(false), 500);
  }

  private computeGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }

  private formatTime(date: Date): string {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  private loadSavedAccent(): ThemeAccent {
    const savedName = localStorage.getItem('eems_login_accent');
    return this.accents.find((a) => a.name === savedName) ?? this.accents[0];
  }
}
