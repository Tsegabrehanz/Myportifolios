import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EmployeeAddress, UpsertAddressRequest } from '../models/employee-address.model';

@Injectable({ providedIn: 'root' })
export class EmployeeAddressApiService {
  constructor(private http: HttpClient) {}

  get(employeeId: number): Observable<EmployeeAddress> {
    return this.http.get<EmployeeAddress>(`${environment.apiUrl}/employees/${employeeId}/address`);
  }

  upsert(employeeId: number, request: UpsertAddressRequest): Observable<EmployeeAddress> {
    return this.http.put<EmployeeAddress>(`${environment.apiUrl}/employees/${employeeId}/address`, request);
  }
}
