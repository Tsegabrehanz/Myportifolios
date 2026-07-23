import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { LeaveRequest, LeaveType } from '../../core/models/leave.model';
import { LeaveBalance } from '../../core/models/leave-balance.model';
import { LeaveApiService } from '../../core/services/leave-api.service';
import { LeaveBalanceApiService } from '../../core/services/leave-balance-api.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-leave',
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
    MatChipsModule,
    MatProgressBarModule
  ],
  templateUrl: './leave.component.html',
  styleUrl: './leave.component.scss'
})
export class LeaveComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leaveBalanceApi = inject(LeaveBalanceApiService);

  readonly leaveTypes: LeaveType[] = ['ANNUAL', 'SICK', 'UNPAID', 'PARENTAL', 'OTHER'];
  readonly requests = signal<LeaveRequest[]>([]);
  readonly balances = signal<LeaveBalance[]>([]);
  readonly displayedColumns = ['employeeName', 'type', 'dates', 'status', 'actions'];
  readonly errorMessage = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly form = this.fb.group({
    type: ['ANNUAL' as LeaveType, Validators.required],
    startDate: ['', Validators.required],
    endDate: ['', Validators.required],
    reason: ['']
  });

  constructor(private leaveApi: LeaveApiService, public authService: AuthService) {}

  ngOnInit(): void {
    this.load();
    this.loadBalances();
  }

  load(): void {
    this.leaveApi.list().subscribe((requests) => this.requests.set(requests));
  }

  loadBalances(): void {
    this.leaveBalanceApi.getMine().subscribe((balances) => this.balances.set(balances.filter((b) => b.tracked)));
  }

  get balancePercentUsed() {
    return (b: LeaveBalance) => {
      const total = b.allocatedDays || 1;
      return Math.min(100, ((b.usedDays + b.pendingDays) / total) * 100);
    };
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    const { type, startDate, endDate, reason } = this.form.getRawValue();
    this.leaveApi
      .submit({ type: type as LeaveType, startDate: startDate!, endDate: endDate!, reason: reason || undefined })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.form.reset({ type: 'ANNUAL' });
          this.load();
          this.loadBalances();
        },
        error: (err) => {
          this.submitting.set(false);
          this.errorMessage.set(this.extractError(err));
        }
      });
  }

  approve(request: LeaveRequest): void {
    this.leaveApi.decide(request.id, 'APPROVED').subscribe(() => {
      this.load();
      this.loadBalances();
    });
  }

  reject(request: LeaveRequest): void {
    this.leaveApi.decide(request.id, 'REJECTED').subscribe(() => {
      this.load();
      this.loadBalances();
    });
  }

  canDecide(request: LeaveRequest): boolean {
    return request.status === 'PENDING' && this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN', 'MANAGER');
  }

  private extractError(err: unknown): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || 'Could not submit leave request. Please try again.';
  }
}
