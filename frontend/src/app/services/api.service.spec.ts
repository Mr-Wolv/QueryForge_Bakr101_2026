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

  it('reruns a run via POST /api/runs/{id}/rerun', () => {
    api.rerunRun('run-1').subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/runs/run-1/rerun' });
    req.flush({ id: 'run-2', state: 'QUEUED' });
  });

  it('downloads artifacts zip via GET format=zip', () => {
    api.downloadArtifactsZip('run-1').subscribe();
    const req = http.expectOne({ method: 'GET', url: '/api/runs/run-1/artifacts?format=zip' });
    req.flush(new Blob(['zip-content'], { type: 'application/zip' }));
  });

  it('lists comparisons via GET /api/comparisons', () => {
    api.getComparisons().subscribe();
    http.expectOne('/api/comparisons').flush([]);
  });

  it('gets a single comparison via GET /api/comparisons/{id}', () => {
    api.getComparison('cmp-1').subscribe();
    http.expectOne('/api/comparisons/cmp-1').flush({ id: 'cmp-1' });
  });

  it('exports a comparison via GET /api/comparisons/{id}/export', () => {
    api.exportComparison('cmp-1', 'html').subscribe();
    const req = http.expectOne({ method: 'GET', url: '/api/comparisons/cmp-1/export?format=html' });
    req.flush(new Blob(['<html></html>'], { type: 'text/html' }));
  });

  it('starts a run via POST /api/runs/{id}/start', () => {
    api.startRun('run-1').subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/runs/run-1/start' });
    req.flush({ id: 'run-1', state: 'RUNNING' });
  });

  it('compares two runs via POST /api/comparisons', () => {
    api.compare('run-1', 'run-2').subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/comparisons' });
    req.flush({ id: 'cmp-1' });
  });

  it('gets run hints via GET /api/runs/{id}/hints', () => {
    api.getHints('run-1').subscribe();
    http.expectOne('/api/runs/run-1/hints').flush([]);
  });

  it('exports a run via GET /api/runs/{id}/export', () => {
    api.exportRun('run-1', 'markdown').subscribe();
    const req = http.expectOne({ method: 'GET', url: '/api/runs/run-1/export?format=markdown' });
    req.flush(new Blob(['# Run'], { type: 'text/markdown' }));
  });

  it('gets run artifacts via GET /api/runs/{id}/artifacts', () => {
    api.getRunArtifacts('run-1').subscribe();
    http.expectOne('/api/runs/run-1/artifacts').flush([]);
  });

  it('gets a single artifact via GET /api/artifacts/{id}', () => {
    api.getArtifact('art-1').subscribe();
    const req = http.expectOne({ method: 'GET', url: '/api/artifacts/art-1' });
    req.flush(new Blob(['data'], { type: 'application/json' }));
  });

  it('gets artifact meta via GET /api/artifacts/{id}/meta', () => {
    api.getArtifactMeta('art-1').subscribe();
    http.expectOne('/api/artifacts/art-1/meta').flush({});
  });

  it('gets a single run via GET /api/runs/{id}', () => {
    api.getRun('run-1').subscribe();
    http.expectOne('/api/runs/run-1').flush({ id: 'run-1' });
  });

  it('gets experiments via GET /api/experiments', () => {
    api.getExperiments().subscribe();
    http.expectOne('/api/experiments').flush([]);
  });

  it('gets a single experiment via GET /api/experiments/{id}', () => {
    api.getExperiment('exp-1').subscribe();
    http.expectOne('/api/experiments/exp-1').flush({ id: 'exp-1' });
  });

  it('gets readiness via GET /api/lab/readiness', () => {
    api.getReadiness().subscribe();
    http.expectOne('/api/lab/readiness').flush({});
  });

  it('gets auth session via GET /api/auth/session', () => {
    api.getAuthSession().subscribe();
    http.expectOne('/api/auth/session').flush({});
  });

  it('logs out via POST /api/auth/logout', () => {
    api.logout().subscribe();
    const req = http.expectOne({ method: 'POST', url: '/api/auth/logout' });
    req.flush({});
  });
});
