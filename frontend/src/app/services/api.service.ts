import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

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

export interface ExperimentRun {
  id: string;
  templateId: string;
  state: string;
  startedAt: string;
  durationMs: number;
  labels: Record<string, string>;
  metrics: {
    testRunDurationMs: number;
    iterationPercentiles: Record<string, number>;
    httpPercentiles: Record<string, number>;
    iterationCount: number;
    rps: number;
  };
  artifacts: { kind: string; contentType: string; location: string; description: string }[];
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
  format: string;
  content: string;
  artifacts: { kind: string; contentType: string; location: string; description: string }[];
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

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = '/api';

  getExperiments(): Observable<ExperimentTemplate[]> {
    return this.http.get<ExperimentTemplate[]>(`${this.base}/experiments`);
  }

  getExperiment(id: string): Observable<any> {
    return this.http.get(`${this.base}/experiments/${id}`);
  }

  getReadiness(): Observable<Readiness> {
    return this.http.get<Readiness>(`${this.base}/lab/readiness`);
  }

  getRuns(): Observable<ExperimentRun[]> {
    return this.http.get<ExperimentRun[]>(`${this.base}/runs`);
  }

  getRun(id: string): Observable<ExperimentRun> {
    return this.http.get<ExperimentRun>(`${this.base}/runs/${id}`);
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

  createRun(templateId: string, parameters: Record<string, string>, clientRequestId?: string): Observable<ExperimentRun> {
    return this.http.post<ExperimentRun>(`${this.base}/runs`, { templateId, parameters, clientRequestId });
  }

  startRun(id: string): Observable<ExperimentRun> {
    return this.http.post<ExperimentRun>(`${this.base}/runs/${id}/start`, {});
  }

  compare(leftRunId: string, rightRunId: string): Observable<ComparisonResult> {
    return this.http.post<ComparisonResult>(`${this.base}/comparisons`, { leftRunId, rightRunId });
  }

  getHints(id: string): Observable<Hint[]> {
    return this.http.get<Hint[]>(`${this.base}/runs/${id}/hints`);
  }

  exportRun(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.http.get(`${this.base}/runs/${id}/export?format=${format}`, { responseType: 'blob' });
  }

  sseEvents(id: string): EventSource {
    const base = window.location.origin;
    return new EventSource(`${base}/api/runs/${id}/events`);
  }
}
