import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiService, PageResponse, ExperimentRun } from './api.service';

const PAGE = {
  content: [{ id: 'run-1' }],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
};

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('serves run history from cache within the TTL', () => {
    api.getRuns().subscribe();
    http.expectOne('/api/runs?page=0&size=20').flush(PAGE);

    let cached: PageResponse<ExperimentRun> | undefined;
    api.getRuns().subscribe(runs => (cached = runs));

    // No second HTTP request: the cache answered it.
    http.expectNone('/api/runs?page=0&size=20');
    expect(cached?.content?.length).toBe(1);
  });

  it('invalidates the run cache so a mutation is visible immediately', () => {
    api.getRuns().subscribe();
    http.expectOne('/api/runs?page=0&size=20').flush(PAGE);

    api.invalidateRuns();
    api.getRuns().subscribe();
    http.expectOne('/api/runs?page=0&size=20').flush(PAGE);
  });

  it('invalidates the run cache after creating a run', () => {
    let created: any;
    api.createRun('QF-001', { RATE: '10' }).subscribe(run => (created = run));
    http.expectOne({ method: 'POST', url: '/api/runs' }).flush({ id: 'run-2' });

    expect(created.id).toBe('run-2');
    // A stale cached list must not survive the mutation.
    api.getRuns().subscribe();
    http.expectOne('/api/runs?page=0&size=20').flush(PAGE);
  });

  it('cancels a run through the documented endpoint', () => {
    let state = '';
    api.cancelRun('run-1').subscribe(res => (state = res.state));

    const req = http.expectOne({ method: 'POST', url: '/api/runs/run-1/cancel' });
    req.flush({ id: 'run-1', state: 'CANCELLING' });

    expect(req.request.body).toEqual({});
    expect(state).toBe('CANCELLING');
  });

  it('requests a run-scoped plan when a run id is supplied', () => {
    api.getParsedPlan('explain-plan', 'run-1').subscribe();
    http.expectOne('/api/runs/run-1/artifacts/explain-plan/plan').flush({ type: 'root', children: [] });
  });

  it('falls back to the legacy catalog plan without a run id', () => {
    api.getParsedPlan('legacy-stem').subscribe();
    http.expectOne('/api/artifacts/legacy-stem/plan').flush({ type: 'root', children: [] });
  });
});
