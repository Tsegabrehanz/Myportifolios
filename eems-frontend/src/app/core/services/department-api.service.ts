import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateDepartmentRequest, Department } from '../models/department.model';
import { DepartmentImportSummaryResponse } from '../models/department-import.model';

@Injectable({ providedIn: 'root' })
export class DepartmentApiService {
  private readonly baseUrl = `${environment.apiUrl}/departments`;

  constructor(private http: HttpClient) {}

  list(): Observable<Department[]> {
    return this.http.get<Department[]>(this.baseUrl);
  }

  create(request: CreateDepartmentRequest): Observable<Department> {
    return this.http.post<Department>(this.baseUrl, request);
  }

  importFile(file: File): Observable<DepartmentImportSummaryResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<DepartmentImportSummaryResponse>(`${this.baseUrl}/import`, formData);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export.csv`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
