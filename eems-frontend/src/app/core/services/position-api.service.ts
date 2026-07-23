import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreatePositionRequest, Position, UpdatePositionRequest } from '../models/position.model';
import { PositionImportSummaryResponse } from '../models/position-import.model';

@Injectable({ providedIn: 'root' })
export class PositionApiService {
  private readonly baseUrl = `${environment.apiUrl}/positions`;

  constructor(private http: HttpClient) {}

  list(): Observable<Position[]> {
    return this.http.get<Position[]>(this.baseUrl);
  }

  create(request: CreatePositionRequest): Observable<Position> {
    return this.http.post<Position>(this.baseUrl, request);
  }

  update(id: number, request: UpdatePositionRequest): Observable<Position> {
    return this.http.put<Position>(`${this.baseUrl}/${id}`, request);
  }

  importFile(file: File): Observable<PositionImportSummaryResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<PositionImportSummaryResponse>(`${this.baseUrl}/import`, formData);
  }

  exportCsv(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export.csv`, { responseType: 'blob' });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
