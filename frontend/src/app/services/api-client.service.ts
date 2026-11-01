import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { CacheService } from './cache.service';

export interface RunQuery {
  state?: string;
  templateId?: string;
  from?: string;
  to?: string;
  sortBy?: string;
  sortDirection?: 'asc' | 'desc';
}

@Injectable({ providedIn: 'root' })
export class ApiClient {
  private http = inject(HttpClient);
  private base = (typeof window !== 'undefined' && (window as any).__QF_API_BASE__) || '/api';
  private cache = inject(CacheService);

  getExperiments<T = any>(): Observable<T[]> {
    return this.http.get<T[]>(`${this.base}/experiments`);
  }

  getExperiment<T = any>(id: string): Observable<T> {
    return this.http.get<T>(`${this.base}/experiments/${id}`);
  }

  getReadiness<T = any>(): Observable<T> {
    return this.http.get<T>(`${this.base}/lab/readiness`);
  }

  getAuthSession<T = any>(): Observable<T> {
    return this.http.get<T>(`${this.base}/auth/session`);
  }

  logout<T = void>(): Observable<T> {
    return this.http.post<T>(`${this.base}/auth/logout`, {});
  }

  getRuns<T = any>(page: number = 0, size: number = 20, query: RunQuery = {}): Observable<T> {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [key, value] of Object.entries(query)) {
      if (value) params = params.set(key, value);
    }
    return this.cache.cached(`runs:${params.toString()}`, () =>
      this.http.get<T>(`${this.base}/runs`, { params })
    );
  }

  getRun<T = any>(id: string): Observable<T> {
    return this.cache.dedup<T>(`run:${id}`, () => this.http.get<T>(`${this.base}/runs/${id}`));
  }

  getRunArtifacts<T = any[]>(id: string): Observable<T[]> {
    return this.http.get<T[]>(`${this.base}/runs/${id}/artifacts`);
  }

  getArtifact(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/artifacts/${id}`, { responseType: 'blob' });
  }

  getArtifactMeta<T = any>(artifactId: string): Observable<T> {
    return this.http.get<T>(`${this.base}/artifacts/${artifactId}/meta`);
  }

  getParsedPlan<T = any>(artifactId: string, runId?: string): Observable<T> {
    const url = runId
      ? `${this.base}/runs/${encodeURIComponent(runId)}/artifacts/${encodeURIComponent(artifactId)}/plan`
      : `${this.base}/artifacts/${encodeURIComponent(artifactId)}/plan`;
    return this.http.get<T>(url);
  }

  createRun<T = any>(templateId: string, parameters: Record<string, string>, clientRequestId?: string): Observable<T> {
    return this.http.post<T>(`${this.base}/runs`, { templateId, parameters, clientRequestId }).pipe(
      tap({ next: () => this.cache.invalidateRuns() })
    );
  }

  startRun<T = any>(id: string): Observable<T> {
    return this.http.post<T>(`${this.base}/runs/${id}/start`, {}).pipe(
      tap({ next: () => this.cache.invalidateRuns() })
    );
  }

  cancelRun<T = { id: string; state: string }>(id: string): Observable<T> {
    return this.http.post<T>(`${this.base}/runs/${id}/cancel`, {}).pipe(
      tap({ next: () => this.cache.invalidateRuns() })
    );
  }

  rerunRun<T = any>(id: string): Observable<T> {
    return this.http.post<T>(`${this.base}/runs/${id}/rerun`, {}).pipe(
      tap({ next: () => this.cache.invalidateRuns() })
    );
  }

  downloadArtifactsZip(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/runs/${id}/artifacts?format=zip`, { responseType: 'blob' });
  }

  compare<T = any>(leftRunId: string, rightRunId: string): Observable<T> {
    return this.http.post<T>(`${this.base}/comparisons`, { leftRunId, rightRunId });
  }

  getComparisons<T = any[]>(): Observable<T[]> {
    return this.http.get<T[]>(`${this.base}/comparisons`);
  }

  getComparison<T = any>(id: string): Observable<T> {
    return this.http.get<T>(`${this.base}/comparisons/${id}`);
  }

  exportComparison(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.http.get(`${this.base}/comparisons/${id}/export?format=${format}`, { responseType: 'blob' });
  }

  getHints<T = any[]>(id: string): Observable<T[]> {
    return this.http.get<T[]>(`${this.base}/runs/${id}/hints`);
  }

  exportRun(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.http.get(`${this.base}/runs/${id}/export?format=${format}`, { responseType: 'blob' });
  }

  invalidateRuns(): void {
    this.cache.invalidateRuns();
  }
}
