import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { CompareComponent } from './compare.component';
import { ApiService, ExperimentRun } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { signal } from '@angular/core';

function run(id: string): ExperimentRun {
  return {
    id,
    templateId: 'QF-001',
    state: 'SUCCEEDED',
    startedAt: '2026-11-01T00:00:00Z',
    durationMs: 1000,
    labels: {},
    parameters: {},
    metrics: { testRunDurationMs: 1000, iterationPercentiles: {}, httpPercentiles: {}, iterationCount: 0, rps: 0 },
    artifacts: [],
  };
}

function setup(api: any) {
  TestBed.configureTestingModule({
    imports: [CompareComponent],
    providers: [
      provideRouter([]),
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: { session: signal({ authEnabled: false, authenticated: false, operator: true, displayName: '' }), canOperate: () => true } },
    ],
  });
  const fixture = TestBed.createComponent(CompareComponent);
  fixture.detectChanges();
  return fixture;
}

describe('CompareComponent', () => {
  it('refuses to compare a run with itself and never calls the API', () => {
    const compare = vi.fn(() => of({}));
    const fixture = setup({
      getRuns: () => of({ content: [run('run-a')], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }),
      getComparisons: () => of([]),
      compare,
    });
    const component = fixture.componentInstance;
    component.a = 'run-a';
    component.candidates = ['run-a'];

    component.compare();
    fixture.detectChanges();

    expect(compare).not.toHaveBeenCalled();
    expect(component.error).toBeUndefined();
    expect(component.batchResults).toHaveLength(0);
  });

  it('surfaces the backend detail instead of a generic failure message', () => {
    const fixture = setup({
      getRuns: () => of({ content: [run('run-a'), run('run-b')], page: 0, size: 20, totalElements: 2, totalPages: 1, hasNext: false }),
      getComparisons: () => of([]),
      // The interceptor unwraps RFC 7807 to the top level, but the
      // component's error formatter still expects the original HttpErrorResponse
      // shape for now, so keep the detail under `error` in this unit test.
      compare: () => throwError(() => ({ error: { detail: 'run ids must differ' } })),
    });
    const component = fixture.componentInstance;
    component.a = 'run-a';
    component.candidates = ['run-b'];

    component.compare();
    fixture.detectChanges();

    expect(component.error).toBeUndefined();
    expect(component.batchResults).toHaveLength(1);
    expect(component.batchResults[0].error).toBe('run ids must differ');
  });
});
