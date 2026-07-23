import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AdminUserApiService } from '../../core/services/admin-user-api.service';
import { AuthService } from '../../core/services/auth.service';
import { AppSettingsApiService } from '../../core/services/app-settings-api.service';
import { BrandingService } from '../../core/services/branding.service';
import { EmployeeIdFormatApiService } from '../../core/services/employee-id-format-api.service';
import { ALL_ROLES, UserSummary } from '../../core/models/user-admin.model';
import { UserRole } from '../../core/models/auth.model';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatSlideToggleModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './user-management.component.html',
  styleUrl: './user-management.component.scss'
})
export class UserManagementComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminUserApiService);
  private readonly authService = inject(AuthService);
  private readonly appSettingsApi = inject(AppSettingsApiService);
  private readonly employeeIdFormatApi = inject(EmployeeIdFormatApiService);
  public readonly brandingService = inject(BrandingService);

  readonly users = signal<UserSummary[]>([]);
  readonly displayedColumns = ['email', 'employee', 'role', 'enabled', 'actions'];
  readonly roles = ALL_ROLES;
  readonly errorMessage = signal<string | null>(null);
  readonly savingId = signal<number | null>(null);

  readonly hasCustomLogo = signal(false);
  readonly uploadingLogo = signal(false);
  readonly logoErrorMessage = signal<string | null>(null);

  readonly employeeIdExample = signal<string | null>(null);
  readonly savingEmployeeIdFormat = signal(false);
  readonly employeeIdFormatErrorMessage = signal<string | null>(null);
  readonly employeeIdFormatForm = this.fb.group({
    prefix: ['', Validators.required],
    suffix: ['']
  });

  @ViewChild('logoInput') logoInput!: ElementRef<HTMLInputElement>;

  // Shown once, right after a reset - can't be retrieved again after
  // leaving this state, same reasoning as the employee-creation flow.
  readonly resetPasswordFor = signal<UserSummary | null>(null);
  readonly resetTemporaryPassword = signal<string | null>(null);
  readonly copied = signal(false);

  ngOnInit(): void {
    this.load();
    this.appSettingsApi.status().subscribe((status) => this.hasCustomLogo.set(status.hasCustomLogo));
    this.loadEmployeeIdFormat();
  }

  loadEmployeeIdFormat(): void {
    this.employeeIdFormatApi.get().subscribe((format) => {
      this.employeeIdFormatForm.patchValue({ prefix: format.prefix, suffix: format.suffix });
      this.employeeIdExample.set(format.exampleNextCode);
    });
  }

  saveEmployeeIdFormat(): void {
    if (this.employeeIdFormatForm.invalid) {
      return;
    }
    const { prefix, suffix } = this.employeeIdFormatForm.getRawValue();
    this.savingEmployeeIdFormat.set(true);
    this.employeeIdFormatErrorMessage.set(null);
    this.employeeIdFormatApi.update({ prefix: prefix!, suffix: suffix ?? '' }).subscribe({
      next: (format) => {
        this.savingEmployeeIdFormat.set(false);
        this.employeeIdExample.set(format.exampleNextCode);
      },
      error: (err) => {
        this.savingEmployeeIdFormat.set(false);
        this.employeeIdFormatErrorMessage.set(this.extractError(err));
      }
    });
  }

  load(): void {
    this.api.list().subscribe((users) => this.users.set(users));
  }

  isSelf(user: UserSummary): boolean {
    return user.email.toLowerCase() === this.authService.currentUser()?.email?.toLowerCase();
  }

  changeRole(user: UserSummary, newRole: UserRole): void {
    if (newRole === user.role) {
      return;
    }
    this.errorMessage.set(null);
    this.savingId.set(user.id);
    this.api.updateRole(user.id, newRole).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.replaceUser(updated);
      },
      error: (err) => {
        this.savingId.set(null);
        this.errorMessage.set(this.extractError(err));
        this.load(); // revert the select to the actual server state
      }
    });
  }

  toggleEnabled(user: UserSummary): void {
    this.errorMessage.set(null);
    this.savingId.set(user.id);
    this.api.updateEnabled(user.id, !user.enabled).subscribe({
      next: (updated) => {
        this.savingId.set(null);
        this.replaceUser(updated);
      },
      error: (err) => {
        this.savingId.set(null);
        this.errorMessage.set(this.extractError(err));
        this.load();
      }
    });
  }

  resetPassword(user: UserSummary): void {
    this.errorMessage.set(null);
    this.savingId.set(user.id);
    this.api.resetPassword(user.id).subscribe({
      next: (result) => {
        this.savingId.set(null);
        this.replaceUser(result.user);
        this.resetPasswordFor.set(result.user);
        this.resetTemporaryPassword.set(result.temporaryPassword);
      },
      error: (err) => {
        this.savingId.set(null);
        this.errorMessage.set(this.extractError(err));
      }
    });
  }

  copyTemporaryPassword(): void {
    const password = this.resetTemporaryPassword();
    if (!password) {
      return;
    }
    navigator.clipboard.writeText(password).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  dismissResetPanel(): void {
    this.resetPasswordFor.set(null);
    this.resetTemporaryPassword.set(null);
  }

  private replaceUser(updated: UserSummary): void {
    this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
  }

  triggerLogoPicker(): void {
    this.logoInput.nativeElement.click();
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadingLogo.set(true);
    this.logoErrorMessage.set(null);
    this.appSettingsApi.upload(file).subscribe({
      next: () => {
        this.uploadingLogo.set(false);
        this.hasCustomLogo.set(true);
        this.brandingService.refresh();
      },
      error: (err) => {
        this.uploadingLogo.set(false);
        this.logoErrorMessage.set(this.extractError(err));
      },
      complete: () => {
        input.value = '';
      }
    });
  }

  removeLogo(): void {
    if (!confirm('Revert to the default EEMS logo?')) {
      return;
    }
    this.appSettingsApi.remove().subscribe(() => {
      this.hasCustomLogo.set(false);
      this.brandingService.refresh();
    });
  }

  private extractError(err: unknown): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || 'Something went wrong. Please try again.';
  }
}
