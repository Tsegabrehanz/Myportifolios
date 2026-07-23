import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { JobPostingApiService } from '../../core/services/job-posting-api.service';
import { DepartmentApiService } from '../../core/services/department-api.service';
import { PositionApiService } from '../../core/services/position-api.service';
import { AuthService } from '../../core/services/auth.service';
import { JobPosting, JobPostingStatus, JobPostingVisibility } from '../../core/models/job-posting.model';
import { Department } from '../../core/models/department.model';
import { Position } from '../../core/models/position.model';

@Component({
  selector: 'app-job-posting-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './job-posting-list.component.html',
  styleUrl: './job-posting-list.component.scss'
})
export class JobPostingListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  public readonly authService = inject(AuthService);

  readonly postings = signal<JobPosting[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly positions = signal<Position[]>([]);
  readonly visibilities: JobPostingVisibility[] = ['INTERNAL', 'EXTERNAL', 'BOTH'];
  readonly statuses: JobPostingStatus[] = ['DRAFT', 'OPEN', 'CLOSED'];

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly expandedId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly form = this.fb.group({
    title: ['', Validators.required],
    description: [''],
    departmentId: [null as number | null],
    positionId: [null as number | null],
    visibility: ['INTERNAL' as JobPostingVisibility, Validators.required],
    status: ['DRAFT' as JobPostingStatus],
    location: [''],
    postedDate: [new Date().toISOString().substring(0, 10), Validators.required],
    closingDate: ['']
  });

  constructor(
    private jobPostingApi: JobPostingApiService,
    private departmentApi: DepartmentApiService,
    private positionApi: PositionApiService
  ) {}

  get canManage(): boolean {
    return this.authService.hasAnyRole('SUPER_ADMIN', 'HR_ADMIN');
  }

  get isEditing(): boolean {
    return this.editingId() !== null;
  }

  ngOnInit(): void {
    this.load();
    if (this.canManage) {
      forkJoin({
        departments: this.departmentApi.list(),
        positions: this.positionApi.list()
      }).subscribe(({ departments, positions }) => {
        this.departments.set(departments);
        this.positions.set(positions);
      });
    }
  }

  load(): void {
    this.loading.set(true);
    this.jobPostingApi.list().subscribe({
      next: (postings) => {
        this.postings.set(postings);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(this.extractError(err, 'Could not load job postings.'));
      }
    });
  }

  toggleForm(): void {
    this.formOpen.update((v) => !v);
    if (!this.formOpen()) {
      this.cancelEdit();
    }
  }

  toggleExpand(posting: JobPosting): void {
    this.expandedId.set(this.expandedId() === posting.id ? null : posting.id);
  }

  startEdit(posting: JobPosting): void {
    this.formOpen.set(true);
    this.editingId.set(posting.id);
    this.form.patchValue({
      title: posting.title,
      description: posting.description ?? '',
      departmentId: posting.departmentId,
      positionId: posting.positionId,
      visibility: posting.visibility,
      status: posting.status,
      location: posting.location ?? '',
      postedDate: posting.postedDate,
      closingDate: posting.closingDate ?? ''
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.form.reset({
      visibility: 'INTERNAL',
      status: 'DRAFT',
      postedDate: new Date().toISOString().substring(0, 10)
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    const { title, description, departmentId, positionId, visibility, status, location, postedDate, closingDate } = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);

    const editingId = this.editingId();
    if (editingId !== null) {
      this.jobPostingApi
        .update(editingId, {
          title: title || undefined,
          description: description || undefined,
          departmentId: departmentId || undefined,
          positionId: positionId || undefined,
          visibility: visibility || undefined,
          status: status || undefined,
          location: location || undefined,
          closingDate: closingDate || undefined
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.formOpen.set(false);
            this.cancelEdit();
            this.load();
          },
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err, 'Could not update this job posting.'));
          }
        });
    } else {
      this.jobPostingApi
        .create({
          title: title!,
          description: description || undefined,
          departmentId: departmentId || undefined,
          positionId: positionId || undefined,
          visibility: visibility!,
          location: location || undefined,
          postedDate: postedDate!,
          closingDate: closingDate || undefined
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.formOpen.set(false);
            this.cancelEdit();
            this.load();
          },
          error: (err) => {
            this.saving.set(false);
            this.errorMessage.set(this.extractError(err, 'Could not create this job posting.'));
          }
        });
    }
  }

  deletePosting(posting: JobPosting): void {
    if (!confirm(`Delete job posting "${posting.title}"? This cannot be undone.`)) {
      return;
    }
    this.deletingId.set(posting.id);
    this.jobPostingApi.delete(posting.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.load();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.errorMessage.set(this.extractError(err, 'Could not delete this job posting.'));
      }
    });
  }

  private extractError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } })?.error?.message;
    return message || fallback;
  }
}
