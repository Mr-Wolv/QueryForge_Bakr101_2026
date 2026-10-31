import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map, tap, shareReplay } from 'rxjs/operators';

export interface ExperimentTemplate {
  id: string;
  title: string;
  purpose: string;
  hypothesis: string;
  parameters: { name: string; type: string; unit: string; min?: any; max?: any; allowed?: string[] }[];
  estimatedDuration: string;
  destructive: boolean;
  outputs: string[];
  reportPath: string;
  planPaths: string[];
}

export interface RunArtifact {
  artifactId: string;
  kind: string;
  contentType: string;
  description: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ExperimentRun {
  id: string;
  templateId: string;
  state: string;
  startedAt: string;
  durationMs: number;
  labels: Record<string, string>;
  parameters: Record<string, string>;
  metrics: {
    testRunDurationMs: number;
    iterationPercentiles: Record<string, number>;
    httpPercentiles: Record<string, number>;
    iterationCount: number;
    rps: number;
  };
  artifacts: RunArtifact[];
}

export interface Readiness {
  api: { status: string };
  database: { status: string; version: string; rowCount: number };
  k6: { status: string; path: string };
  fingerprint: { appCommit: string; javaVersion: string; postgresVersion: string; checkedAt: string };
}

export interface Hint {
  id: string;
  severity: 'info' | 'suggestion' | 'warning';
  title: string;
  body: string;
}

export interface MetricDeltaValue {
  left: number;
  right: number;
  delta: number;
  deltaPercent: number | null;
  unit: string;
}

export interface ComparisonResult {
  id: string;
  leftRunId: string;
  rightRunId: string;
  baseline: string;
  candidate: string;
  verdict: {
    compatible: boolean;
    blockers: string[];
    warnings: string[];
    diff: Record<string, string>;
  };
  metricsDelta?: Record<string, MetricDeltaValue>;
  format: string;
  content: string;
  artifacts: RunArtifact[];
  createdAt: string;
  createdBy: string | null;
}

export interface ChartDataPoint {
  label: string;
  value: number;
}

export interface SseState {
  state: string;
}

export interface RunEvent {
  state: string;
  durationMs?: number;
  message?: string;
}

export interface AuthSession {
  authEnabled: boolean;
  authenticated: boolean;
  operator: boolean;
  displayName: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = (typeof window !== 'undefined' && (window as any).__QF_API_BASE__) || '/api';

  private readonly cache = new Map<string, { expiry: number; value: any }>();
  private readonly ttlMs = 30_000;

  private readonly inflight = new Map<string, Observable<any>>();

  private cached<T>(key: string, request: () => Observable<T>): Observable<T> {
    const hit = this.cache.get(key);
    if (hit && hit.expiry > Date.now()) {
      return of(hit.value);
    }
    return request().pipe(
      tap(v => this.cache.set(key, { expiry: Date.now() + this.ttlMs, value: v })),
      shareReplay(1)
    );
  }

  /** Drop cached run history so the next read reflects a just-mutated run. */
  invalidateRuns(): void {
    this.cache.delete('runs');
    for (const key of Array.from(this.inflight.keys())) {
      if (key.startsWith('run:')) this.inflight.delete(key);
    }
  }

  private dedup<T>(key: string, request: () => Observable<T>): Observable<T> {
    const existing = this.inflight.get(key);
    if (existing) {
      return existing as Observable<T>;
    }
    const obs = request().pipe(
      tap({
        finalize: () => this.inflight.delete(key)
      }),
      shareReplay(1)
    );
    this.inflight.set(key, obs);
    return obs;
  }

  getExperiments(): Observable<ExperimentTemplate[]> {
    return this.http.get<ExperimentTemplate[]>(`${this.base}/experiments`);
  }

  getExperiment(id: string): Observable<any> {
    return this.http.get(`${this.base}/experiments/${id}`);
  }

  getReadiness(): Observable<Readiness> {
    return this.http.get<Readiness>(`${this.base}/lab/readiness`);
  }

  getAuthSession(): Observable<AuthSession> {
    return this.http.get<AuthSession>(`${this.base}/auth/session`);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {});
  }

  getRuns(page: number = 0, size: number = 20): Observable<PageResponse<ExperimentRun>> {
    return this.http.get<PageResponse<ExperimentRun>>(`${this.base}/runs?page=${page}&size=${size}`);
  }

  getRun(id: string): Observable<ExperimentRun> {
    return this.dedup<ExperimentRun>(`run:${id}`, () =>
      this.http.get<ExperimentRun>(`${this.base}/runs/${id}`)
    );
  }

  getRunArtifacts(id: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.base}/runs/${id}/artifacts`);
  }

  getArtifact(artifactId: string): Observable<Blob> {
    return this.http.get(`${this.base}/artifacts/${artifactId}`, { responseType: 'blob' });
  }

  getArtifactMeta(artifactId: string): Observable<any> {
    return this.http.get(`${this.base}/artifacts/${artifactId}/meta`);
  }

  getParsedPlan(artifactId: string, runId?: string): Observable<any> {
    const url = runId
      ? `${this.base}/runs/${encodeURIComponent(runId)}/artifacts/${encodeURIComponent(artifactId)}/plan`
      : `${this.base}/artifacts/${encodeURIComponent(artifactId)}/plan`;
    return this.http.get(url);
  }

  createRun(templateId: string, parameters: Record<string, string>, clientRequestId?: string): Observable<ExperimentRun> {
    return this.http.post<ExperimentRun>(`${this.base}/runs`, { templateId, parameters, clientRequestId }).pipe(
      tap({ next: () => this.invalidateRuns() })
    );
  }

  startRun(id: string): Observable<ExperimentRun> {
    return this.http.post<ExperimentRun>(`${this.base}/runs/${id}/start`, {}).pipe(
      tap({ next: () => this.invalidateRuns() })
    );
  }

  cancelRun(id: string): Observable<{ id: string; state: string }> {
    return this.http.post<{ id: string; state: string }>(`${this.base}/runs/${id}/cancel`, {}).pipe(
      tap({ next: () => this.invalidateRuns() })
    );
  }

  rerunRun(id: string): Observable<ExperimentRun> {
    return this.http.post<ExperimentRun>(`${this.base}/runs/${id}/rerun`, {}).pipe(
      tap({ next: () => this.invalidateRuns() })
    );
  }

  downloadArtifactsZip(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/runs/${id}/artifacts?format=zip`, { responseType: 'blob' });
  }

  compare(leftRunId: string, rightRunId: string): Observable<ComparisonResult> {
    return this.http.post<ComparisonResult>(`${this.base}/comparisons`, { leftRunId, rightRunId });
  }

  getComparisons(): Observable<ComparisonResult[]> {
    return this.http.get<ComparisonResult[]>(`${this.base}/comparisons`);
  }

  getComparison(id: string): Observable<ComparisonResult> {
    return this.http.get<ComparisonResult>(`${this.base}/comparisons/${id}`);
  }

  exportComparison(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.http.get(`${this.base}/comparisons/${id}/export?format=${format}`, { responseType: 'blob' });
  }

  getHints(id: string): Observable<Hint[]> {
    return this.http.get<Hint[]>(`${this.base}/runs/${id}/hints`);
  }

  exportRun(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.http.get(`${this.base}/runs/${id}/export?format=${format}`, { responseType: 'blob' });
  }

  sseEvents(id: string): EventSource {
    return new EventSource(`${this.base}/runs/${id}/events`);
  }
}
