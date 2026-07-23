import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateLeaveRequest, LeaveRequest, LeaveStatus } from '../models/leave.model';

@Injectable({ providedIn: 'root' })
export class LeaveApiService {
  private readonly baseUrl = `${environment.apiUrl}/leave-requests`;

  constructor(private http: HttpClient) {}

  list(): Observable<LeaveRequest[]> {
    return this.http.get<LeaveRequest[]>(this.baseUrl);
  }

  submit(request: CreateLeaveRequest): Observable<LeaveRequest> {
    return this.http.post<LeaveRequest>(this.baseUrl, request);
  }

  decide(id: number, decision: LeaveStatus): Observable<LeaveRequest> {
    return this.http.patch<LeaveRequest>(`${this.baseUrl}/${id}/decision`, { decision });
  }
}
