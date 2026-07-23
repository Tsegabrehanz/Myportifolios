import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeApiService } from '../../core/services/employee-api.service';
import { LeaveApiService } from '../../core/services/leave-api.service';
import { ReportApiService } from '../../core/services/report-api.service';
import { HrSummaryReport } from '../../core/models/report.model';
import { LeaveRequest, LeaveType } from '../../core/models/leave.model';
import { ChartComponent } from '../../shared/chart/chart.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTooltipModule,
    ChartComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly visibleEmployeeCount = signal<number | null>(null);
  readonly pendingLeaveCount = signal<number | null>(null);
  readonly myLeaveCount = signal<number | null>(null);
  readonly hrSummary = signal<HrSummaryReport | null>(null);
  readonly allLeaveRequests = signal<LeaveRequest[]>([]);

  // --- Live refresh ---
  readonly lastUpdated = signal<Date | null>(null);
  readonly refreshing = signal(false);
  readonly secondsSinceUpdate = signal(0);
  private refreshTimer?: ReturnType<typeof setInterval>;
  private tickTimer?: ReturnType<typeof setInterval>;
  private static readonly REFRESH_INTERVAL_MS = 30000;

  // --- Expandable pending-approvals widget ---
  readonly approvalsExpanded = signal(false);
  readonly decidingId = signal<number | null>(null);

  // --- Quick "Request leave" widget ---
  readonly requestFormOpen = signal(false);
  readonly submittingLeave = signal(false);
  readonly leaveTypes: LeaveType[] = ['ANNUAL', 'SICK', 'UNPAID', 'PARENTAL', 'OTHER'];
  readonly quickLeaveForm = this.fb.group({
    type: ['ANNUAL' as LeaveType, Validators.required],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
    reason: ['']
  });

  constructor(
    public authService: AuthService,
    private employeeApi: EmployeeApiService,
    private leaveApi: LeaveApiService,
    private reportApi: ReportApiService
  ) {}

  ngOnInit(): void {
    this.loadAll();

    this.refreshTimer = setInterval(() => this.loadAll(), DashboardComponent.REFRESH_INTERVAL_MS);
    this.tickTimer = setInterval(() => {
      if (this.lastUpdated()) {
        this.secondsSinceUpdate.set(Math.floor((Date.now() - this.lastUpdated()!.getTime()) / 1000));
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    if (this.tickTimer) clearInterval(this.tickTimer);
  }

  loadAll(): void {
    this.refreshing.set(true);
    forkJoin({
      employees: this.employeeApi.list(),
      leaveRequests: this.leaveApi.list()
    }).subscribe(({ employees, leaveRequests }) => {
      this.visibleEmployeeCount.set(employees.length);
      this.allLeaveRequests.set(leaveRequests);
      this.pendingLeaveCount.set(leaveRequests.filter((l) => l.status === 'PENDING').length);
      this.myLeaveCount.set(leaveRequests.length);
      this.refreshing.set(false);
      this.lastUpdated.set(new Date());
      this.secondsSinceUpdate.set(0);
    });

    if (this.canViewAnalytics) {
      this.reportApi.getHrSummary().subscribe((report) => this.hrSummary.set(report));
    }
  }

  get canViewAnalytics(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'AUDITOR');
  }

  get canManageDepartments(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN');
  }

  get canDecideLeave(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'MANAGER');
  }

  get pendingRequests(): LeaveRequest[] {
    return this.allLeaveRequests().filter((l) => l.status === 'PENDING');
  }

  /**
   * Both were plain getters before - same bug class as the Analytics
   * page (see that component's comment for the full explanation): a new
   * array every change-detection check meant <app-chart> rebuilt its
   * Chart.js instance every check, which schedules a requestAnimationFrame
   * that Angular's zone intercepts and triggers ANOTHER check from -
   * an infinite render loop that pegs the CPU and can hang/crash the
   * tab. This page being the very first thing anyone sees post-login
   * makes it the most likely single cause of "the browser freezes when
   * I use this app" reports. computed() caches its result and only
   * recomputes when hrSummary() itself changes.
   */
  readonly departmentLabels = computed(() => this.hrSummary()?.headcountByDepartment.map((d) => d.departmentName) ?? []);
  readonly departmentData = computed(() => this.hrSummary()?.headcountByDepartment.map((d) => d.headcount) ?? []);

  get greeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
  }

  toggleApprovals(): void {
    this.approvalsExpanded.update((v) => !v);
  }

  approve(request: LeaveRequest): void {
    this.decidingId.set(request.id);
    this.leaveApi.decide(request.id, 'APPROVED').subscribe({
      next: () => {
        this.decidingId.set(null);
        this.snackBar.open(`Approved ${request.employeeName}'s ${request.type.toLowerCase()} leave.`, 'Dismiss', { duration: 3000 });
        this.loadAll();
      },
      error: () => this.decidingId.set(null)
    });
  }

  reject(request: LeaveRequest): void {
    this.decidingId.set(request.id);
    this.leaveApi.decide(request.id, 'REJECTED').subscribe({
      next: () => {
        this.decidingId.set(null);
        this.snackBar.open(`Rejected ${request.employeeName}'s ${request.type.toLowerCase()} leave.`, 'Dismiss', { duration: 3000 });
        this.loadAll();
      },
      error: () => this.decidingId.set(null)
    });
  }

  toggleRequestForm(): void {
    this.requestFormOpen.update((v) => !v);
  }

  submitQuickLeave(): void {
    if (this.quickLeaveForm.invalid) {
      return;
    }
    const { type, startDate, endDate, reason } = this.quickLeaveForm.getRawValue();
    this.submittingLeave.set(true);
    this.leaveApi.submit({ type: type as LeaveType, startDate: startDate!, endDate: endDate!, reason: reason || undefined }).subscribe({
      next: () => {
        this.submittingLeave.set(false);
        this.quickLeaveForm.reset({ type: 'ANNUAL' });
        this.requestFormOpen.set(false);
        this.snackBar.open('Leave request submitted.', 'Dismiss', { duration: 3000 });
        this.loadAll();
      },
      error: () => this.submittingLeave.set(false)
    });
  }
}
