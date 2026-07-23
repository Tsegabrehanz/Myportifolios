import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateEmployeeRequest, CreateEmployeeResponse, Employee, UpdateEmployeeRequest } from '../models/employee.model';
import { ImportSummaryResponse } from '../models/employee-import.model';

@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private readonly baseUrl = `${environment.apiUrl}/employees`;

  constructor(private http: HttpClient) {}

  list(): Observable<Employee[]> {
    return this.http.get<Employee[]>(this.baseUrl);
  }

  getById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateEmployeeRequest): Observable<CreateEmployeeResponse> {
    return this.http.post<CreateEmployeeResponse>(this.baseUrl, request);
  }

  update(id: number, request: UpdateEmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, request);
  }

  offboard(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/offboard`, {});
  }

  importFile(file: File): Observable<ImportSummaryResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportSummaryResponse>(`${this.baseUrl}/import`, formData);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export.csv`, { responseType: 'blob' });
  }
}
