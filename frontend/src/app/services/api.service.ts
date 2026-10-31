import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from './api-client.service';
import { CacheService } from './cache.service';
import { SseService } from './sse.service';

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
  private apiClient = inject(ApiClient);
  private cache = inject(CacheService);
  private sse = inject(SseService);

  getExperiments(): Observable<ExperimentTemplate[]> {
    return this.apiClient.getExperiments<ExperimentTemplate>();
  }

  getExperiment(id: string): Observable<any> {
    return this.apiClient.getExperiment<any>(id);
  }

  getReadiness(): Observable<Readiness> {
    return this.apiClient.getReadiness<Readiness>();
  }

  getAuthSession(): Observable<AuthSession> {
    return this.apiClient.getAuthSession<AuthSession>();
  }

  logout(): Observable<void> {
    return this.apiClient.logout<void>();
  }

  getRuns(page: number = 0, size: number = 20): Observable<PageResponse<ExperimentRun>> {
    return this.apiClient.getRuns<PageResponse<ExperimentRun>>(page, size);
  }

  getRun(id: string): Observable<ExperimentRun> {
    return this.apiClient.getRun<ExperimentRun>(id);
  }

  getRunArtifacts(id: string): Observable<any[]> {
    return this.apiClient.getRunArtifacts<any[]>(id);
  }

  getArtifact(artifactId: string): Observable<Blob> {
    return this.apiClient.getArtifact(artifactId);
  }

  getArtifactMeta(artifactId: string): Observable<any> {
    return this.apiClient.getArtifactMeta<any>(artifactId);
  }

  getParsedPlan(artifactId: string, runId?: string): Observable<any> {
    return this.apiClient.getParsedPlan<any>(artifactId, runId);
  }

  createRun(templateId: string, parameters: Record<string, string>, clientRequestId?: string): Observable<ExperimentRun> {
    return this.apiClient.createRun<ExperimentRun>(templateId, parameters, clientRequestId);
  }

  startRun(id: string): Observable<ExperimentRun> {
    return this.apiClient.startRun<ExperimentRun>(id);
  }

  cancelRun(id: string): Observable<{ id: string; state: string }> {
    return this.apiClient.cancelRun<{ id: string; state: string }>(id);
  }

  rerunRun(id: string): Observable<ExperimentRun> {
    return this.apiClient.rerunRun<ExperimentRun>(id);
  }

  downloadArtifactsZip(id: string): Observable<Blob> {
    return this.apiClient.downloadArtifactsZip(id);
  }

  compare(leftRunId: string, rightRunId: string): Observable<ComparisonResult> {
    return this.apiClient.compare<ComparisonResult>(leftRunId, rightRunId);
  }

  getComparisons(): Observable<ComparisonResult[]> {
    return this.apiClient.getComparisons<ComparisonResult>();
  }

  getComparison(id: string): Observable<ComparisonResult> {
    return this.apiClient.getComparison<ComparisonResult>(id);
  }

  exportComparison(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.apiClient.exportComparison(id, format);
  }

  getHints(id: string): Observable<Hint[]> {
    return this.apiClient.getHints<Hint>(id);
  }

  exportRun(id: string, format: 'markdown' | 'html' = 'markdown'): Observable<Blob> {
    return this.apiClient.exportRun(id, format);
  }

  sseEvents(id: string): EventSource {
    return this.sse.sseEvents(id);
  }

  invalidateRuns(): void {
    this.cache.invalidateRuns();
  }
}
