import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { DepartmentApiService } from '../../core/services/department-api.service';
import { PositionApiService } from '../../core/services/position-api.service';
import { Department } from '../../core/models/department.model';
import { Position } from '../../core/models/position.model';
import { Employee, EmployeeStatus } from '../../core/models/employee.model';

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './employee-form.component.html',
  styleUrl: './employee-form.component.scss'
})
export class EmployeeFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly employeeApi = inject(EmployeeApiService);
  private readonly departmentApi = inject(DepartmentApiService);
  private readonly positionApi = inject(PositionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly isEditMode = signal(false);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly departments = signal<Department[]>([]);
  readonly positions = signal<Position[]>([]);
  readonly potentialManagers = signal<Employee[]>([]);

  readonly statuses: EmployeeStatus[] = ['ONBOARDING', 'ACTIVE', 'ON_LEAVE', 'OFFBOARDING', 'OFFBOARDED'];

  // Shown once, right after a successful create, if the backend
  // generated an email and/or temporary password. Neither can be
  // retrieved again after this - the form stays on this screen showing
  // them until the admin clicks "Done", instead of auto-navigating away.
  readonly generatedEmail = signal<string | null>(null);
  readonly generatedPassword = signal<string | null>(null);
  readonly copiedField = signal<'email' | 'password' | null>(null);

  private employeeId: number | null = null;

  readonly form = this.fb.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: [''], // create-only, optional - auto-generated if left blank
    initialPassword: [''], // create-only, optional - secure temp password generated if left blank
    hireDate: [''], // create-only
    positionId: [null as number | null],
    departmentId: [null as number | null],
    managerId: [null as number | null],
    status: ['ACTIVE' as EmployeeStatus] // edit-only
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.isEditMode.set(!!idParam);
    this.employeeId = idParam ? Number(idParam) : null;

    forkJoin({
      departments: this.departmentApi.list(),
      positions: this.positionApi.list(),
      employees: this.employeeApi.list()
    }).subscribe(({ departments, positions, employees }) => {
      this.departments.set(departments);
      this.positions.set(positions);
      this.potentialManagers.set(employees.filter((e) => e.id !== this.employeeId));

      if (this.isEditMode() && this.employeeId) {
        this.employeeApi.getById(this.employeeId).subscribe((employee) => {
          this.form.patchValue({
            firstName: employee.firstName,
            lastName: employee.lastName,
            positionId: employee.positionId,
            departmentId: employee.departmentId,
            managerId: employee.managerId,
            status: employee.status
          });
          this.loading.set(false);
        });
      } else {
        this.loading.set(false);
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    this.errorMessage.set(null);

    const raw = this.form.getRawValue();

    if (this.isEditMode() && this.employeeId) {
      this.employeeApi
        .update(this.employeeId, {
          firstName: raw.firstName!,
          lastName: raw.lastName!,
          positionId: raw.positionId ?? undefined,
          departmentId: raw.departmentId ?? undefined,
          managerId: raw.managerId ?? undefined,
          status: raw.status!
        })
        .subscribe({
          next: () => this.router.navigate(['/employees']),
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err));
          }
        });
    } else {
      this.employeeApi
        .create({
          firstName: raw.firstName!,
          lastName: raw.lastName!,
          email: raw.email?.trim() || undefined, // blank -> backend generates one
          initialPassword: raw.initialPassword?.trim() || undefined, // blank -> backend generates a secure temp password
          hireDate: raw.hireDate!,
          positionId: raw.positionId ?? undefined,
          departmentId: raw.departmentId ?? undefined,
          managerId: raw.managerId ?? undefined
        })
        .subscribe({
          next: (response) => {
            this.saving.set(false);
            if (response.generatedEmail || response.generatedTemporaryPassword) {
              // Stay on this page and show the credentials - don't
              // navigate away until the admin has had a chance to copy
              // them, since they can't be retrieved again afterward.
              this.generatedEmail.set(response.generatedEmail);
              this.generatedPassword.set(response.generatedTemporaryPassword);
            } else {
              this.router.navigate(['/employees']);
            }
          },
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err));
          }
        });
    }
  }

  copyToClipboard(field: 'email' | 'password', value: string): void {
    navigator.clipboard.writeText(value).then(() => {
      this.copiedField.set(field);
      setTimeout(() => this.copiedField.set(null), 2000);
    });
  }

  doneViewingCredentials(): void {
    this.router.navigate(['/employees']);
  }

  private extractError(err: unknown): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || 'Something went wrong. Please try again.';
  }
}
