import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { OverviewComponent } from './overview.component';
import { ApiService, ExperimentRun, PageResponse } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';

function run(partial: Partial<ExperimentRun> & Pick<ExperimentRun, 'id' | 'startedAt'>): ExperimentRun {
  return {
    templateId: 'QF-001',
    state: 'SUCCEEDED',
    durationMs: 1000,
    labels: {},
    parameters: {},
    metrics: {
      testRunDurationMs: 1000,
      iterationPercentiles: {},
      httpPercentiles: {},
      iterationCount: 0,
      rps: 0,
    },
    artifacts: [],
    ...partial,
  };
}

function page(content: ExperimentRun[]): PageResponse<ExperimentRun> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, hasNext: false };
}

function render(runs: ExperimentRun[]) {
  const api = {
    getRuns: vi.fn(() => of(page(runs))),
    getReadiness: vi.fn(() => of({
      api: { status: 'UP' },
      database: { status: 'UP', version: '17.0', rowCount: 1000 },
      k6: { status: 'UP', path: 'k6' },
      fingerprint: { appCommit: 'abc', javaVersion: '25', postgresVersion: '17.0', checkedAt: '2026-11-01T00:00:00Z' },
    })),
  };
  const auth = {
    session: signal({ authEnabled: false, authenticated: false, operator: true, displayName: '' }),
  };
  TestBed.configureTestingModule({
    imports: [OverviewComponent],
    providers: [
      provideRouter([]),
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: auth },
    ],
  });
  const fixture = TestBed.createComponent(OverviewComponent);
  fixture.detectChanges();
  return fixture;
}

describe('OverviewComponent', () => {
  it('shows the newest run even though the API returns newest first', () => {
    // The API orders by startedAt DESC, so the newest run is element 0.
    const fixture = render([
      run({ id: 'qf-000-run-new', startedAt: '2026-11-01T13:42:32Z', metrics: {
        testRunDurationMs: 1, iterationPercentiles: { p95: 43.4 }, httpPercentiles: {}, iterationCount: 1, rps: 10,
      } }),
      run({ id: 'qf-001-run-old', startedAt: '2026-10-31T01:46:46Z' }),
    ]);

    expect(fixture.componentInstance.latestRun?.id).toBe('qf-000-run-new');
    expect(fixture.nativeElement.textContent).toContain('qf-000-run-new');
  });

  it('renders an em dash instead of a bare unit when p95 is missing', () => {
    const fixture = render([run({ id: 'qf-001-run-cancelled', startedAt: '2026-10-31T01:46:46Z', state: 'CANCELLED' })]);
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('p95: —');
    expect(text).not.toMatch(/p95:\s+ms/);
  });

  it('shows the most recent QF-001 run instead of claiming none is recorded', () => {
    const fixture = render([
      run({ id: 'qf-001-run-aa', startedAt: '2026-10-30T10:00:00Z' }),
      run({ id: 'qf-001-run-bb', startedAt: '2026-10-31T10:00:00Z' }),
    ]);
    const text = fixture.nativeElement.textContent as string;

    expect(fixture.componentInstance.baselineRun?.id).toBe('qf-001-run-bb');
    expect(text).not.toContain('No QF-001 run is recorded yet.');
    expect(text).toContain('qf-001-run-bb');
  });
});
