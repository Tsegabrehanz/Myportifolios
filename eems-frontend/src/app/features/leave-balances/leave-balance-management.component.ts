import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';
import { LeaveBalanceApiService } from '../../core/services/leave-balance-api.service';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { DepartmentApiService } from '../../core/services/department-api.service';
import { LeaveBalance, TRACKED_LEAVE_TYPES } from '../../core/models/leave-balance.model';
import { Employee } from '../../core/models/employee.model';
import { Department } from '../../core/models/department.model';

@Component({
  selector: 'app-leave-balance-management',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    MatSnackBarModule
  ],
  templateUrl: './leave-balance-management.component.html',
  styleUrl: './leave-balance-management.component.scss'
})
export class LeaveBalanceManagementComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leaveBalanceApi = inject(LeaveBalanceApiService);
  private readonly employeeApi = inject(EmployeeApiService);
  private readonly departmentApi = inject(DepartmentApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly balances = signal<LeaveBalance[]>([]);
  readonly employees = signal<Employee[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly trackedTypes = TRACKED_LEAVE_TYPES;
  readonly displayedColumns = ['employeeName', 'leaveType', 'allocatedDays', 'usedDays', 'pendingDays', 'availableDays'];
  readonly saving = signal(false);
  readonly bulkSaving = signal(false);
  readonly carryOverSaving = signal(false);
  readonly currentYear = new Date().getFullYear();

  readonly form = this.fb.group({
    employeeId: [null as number | null, Validators.required],
    leaveType: [this.trackedTypes[0], Validators.required],
    year: [this.currentYear, Validators.required],
    allocatedDays: [20, [Validators.required, Validators.min(0)]]
  });

  readonly bulkForm = this.fb.group({
    leaveType: [this.trackedTypes[0], Validators.required],
    year: [this.currentYear, Validators.required],
    allocatedDays: [20, [Validators.required, Validators.min(0)]],
    departmentId: [null as number | null]
  });

  readonly carryOverForm = this.fb.group({
    leaveType: [this.trackedTypes[0], Validators.required],
    fromYear: [this.currentYear - 1, Validators.required],
    toYear: [this.currentYear, Validators.required],
    maxCarryOverDays: [null as number | null]
  });

  ngOnInit(): void {
    this.load();
    this.departmentApi.list().subscribe((departments) => this.departments.set(departments));
  }

  load(): void {
    forkJoin({
      balances: this.leaveBalanceApi.listAll(this.currentYear),
      employees: this.employeeApi.list()
    }).subscribe(({ balances, employees }) => {
      this.balances.set(balances);
      this.employees.set(employees);
    });
  }

  setAllocation(): void {
    if (this.form.invalid) {
      return;
    }
    const { employeeId, leaveType, year, allocatedDays } = this.form.getRawValue();
    this.saving.set(true);
    this.leaveBalanceApi
      .upsert({ employeeId: employeeId!, leaveType: leaveType!, year: year!, allocatedDays: allocatedDays! })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.load();
        },
        error: () => this.saving.set(false)
      });
  }

  bulkAllocate(): void {
    if (this.bulkForm.invalid) {
      return;
    }
    const { leaveType, year, allocatedDays, departmentId } = this.bulkForm.getRawValue();
    this.bulkSaving.set(true);
    this.leaveBalanceApi
      .bulkAllocate({ leaveType: leaveType!, year: year!, allocatedDays: allocatedDays!, departmentId: departmentId ?? undefined })
      .subscribe({
        next: (result) => {
          this.bulkSaving.set(false);
          this.snackBar.open(`Set ${allocatedDays} day(s) of ${leaveType} for ${result.employeeCount} employee(s).`, 'Dismiss', { duration: 4000 });
          this.load();
        },
        error: (err) => {
          this.bulkSaving.set(false);
          this.snackBar.open(this.extractError(err), 'Dismiss', { duration: 5000 });
        }
      });
  }

  carryOver(): void {
    if (this.carryOverForm.invalid) {
      return;
    }
    const { leaveType, fromYear, toYear, maxCarryOverDays } = this.carryOverForm.getRawValue();
    this.carryOverSaving.set(true);
    this.leaveBalanceApi
      .carryOver({ leaveType: leaveType!, fromYear: fromYear!, toYear: toYear!, maxCarryOverDays: maxCarryOverDays ?? undefined })
      .subscribe({
        next: (result) => {
          this.carryOverSaving.set(false);
          this.snackBar.open(`Carried over ${leaveType} balance for ${result.employeeCount} employee(s) from ${fromYear} to ${toYear}.`, 'Dismiss', { duration: 4000 });
          this.load();
        },
        error: (err) => {
          this.carryOverSaving.set(false);
          this.snackBar.open(this.extractError(err), 'Dismiss', { duration: 5000 });
        }
      });
  }

  private extractError(err: unknown): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || 'Something went wrong.';
  }
}
