import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EmployeeIdFormat, UpdateEmployeeIdFormatRequest } from '../models/employee-id-format.model';

@Injectable({ providedIn: 'root' })
export class EmployeeIdFormatApiService {
  private readonly baseUrl = `${environment.apiUrl}/app-settings/employee-id-format`;

  constructor(private http: HttpClient) {}

  get(): Observable<EmployeeIdFormat> {
    return this.http.get<EmployeeIdFormat>(this.baseUrl);
  }

  /** SUPER_ADMIN/HR_ADMIN only server-side. Only affects employees created after this call. */
  update(request: UpdateEmployeeIdFormatRequest): Observable<EmployeeIdFormat> {
    return this.http.put<EmployeeIdFormat>(this.baseUrl, request);
  }
}
