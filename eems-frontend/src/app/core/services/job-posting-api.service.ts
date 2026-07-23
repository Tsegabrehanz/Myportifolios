import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateJobPostingRequest, JobPosting, UpdateJobPostingRequest } from '../models/job-posting.model';

@Injectable({ providedIn: 'root' })
export class JobPostingApiService {
  private readonly baseUrl = `${environment.apiUrl}/job-postings`;

  constructor(private http: HttpClient) {}

  /** Visibility-scoped server-side: HR/Admin/Auditor see everything, everyone else only OPEN + INTERNAL/BOTH postings. */
  list(): Observable<JobPosting[]> {
    return this.http.get<JobPosting[]>(this.baseUrl);
  }

  getById(id: number): Observable<JobPosting> {
    return this.http.get<JobPosting>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateJobPostingRequest): Observable<JobPosting> {
    return this.http.post<JobPosting>(this.baseUrl, request);
  }

  update(id: number, request: UpdateJobPostingRequest): Observable<JobPosting> {
    return this.http.put<JobPosting>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
