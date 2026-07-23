import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { HrSummaryReport } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportApiService {
  private readonly baseUrl = `${environment.apiUrl}/reports`;

  constructor(private http: HttpClient) {}

  getHrSummary(): Observable<HrSummaryReport> {
    return this.http.get<HrSummaryReport>(`${this.baseUrl}/hr-summary`);
  }

  downloadExcel(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/hr-summary/export.xlsx`, { responseType: 'blob' });
  }

  downloadPdf(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/hr-summary/export.pdf`, { responseType: 'blob' });
  }
}
