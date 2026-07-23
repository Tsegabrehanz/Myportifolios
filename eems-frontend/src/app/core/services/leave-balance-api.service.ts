import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BulkAllocateRequest,
  BulkAllocateResponse,
  CarryOverRequest,
  CarryOverResponse,
  LeaveBalance,
  UpsertLeaveBalanceRequest
} from '../models/leave-balance.model';

@Injectable({ providedIn: 'root' })
export class LeaveBalanceApiService {
  private readonly baseUrl = `${environment.apiUrl}/leave-balances`;

  constructor(private http: HttpClient) {}

  getMine(year?: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(this.baseUrl + '/me', { params: year ? { year } : {} });
  }

  getForEmployee(employeeId: number, year?: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(`${this.baseUrl}/employee/${employeeId}`, { params: year ? { year } : {} });
  }

  listAll(year?: number): Observable<LeaveBalance[]> {
    return this.http.get<LeaveBalance[]>(this.baseUrl, { params: year ? { year } : {} });
  }

  upsert(request: UpsertLeaveBalanceRequest): Observable<LeaveBalance> {
    return this.http.post<LeaveBalance>(this.baseUrl, request);
  }

  bulkAllocate(request: BulkAllocateRequest): Observable<BulkAllocateResponse> {
    return this.http.post<BulkAllocateResponse>(`${this.baseUrl}/bulk-allocate`, request);
  }

  carryOver(request: CarryOverRequest): Observable<CarryOverResponse> {
    return this.http.post<CarryOverResponse>(`${this.baseUrl}/carry-over`, request);
  }
}
