import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuditLogApiService } from '../../core/services/audit-log-api.service';
import { AuditLogEntry } from '../../core/models/audit-log.model';

@Component({
  selector: 'app-audit-log-viewer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatCardModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './audit-log-viewer.component.html',
  styleUrl: './audit-log-viewer.component.scss'
})
export class AuditLogViewerComponent implements OnInit {
  private readonly api = inject(AuditLogApiService);

  readonly entries = signal<AuditLogEntry[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(true);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(25);
  readonly displayedColumns = ['timestamp', 'actorEmail', 'entity', 'action', 'detail'];

  entityFilter = '';
  actorFilter = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.list(this.pageIndex(), this.pageSize(), this.entityFilter || undefined, this.actorFilter || undefined).subscribe((result) => {
      this.entries.set(result.content);
      this.totalElements.set(result.totalElements);
      this.loading.set(false);
    });
  }

  applyFilters(): void {
    this.pageIndex.set(0);
    this.load();
  }

  clearFilters(): void {
    this.entityFilter = '';
    this.actorFilter = '';
    this.applyFilters();
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }
}
