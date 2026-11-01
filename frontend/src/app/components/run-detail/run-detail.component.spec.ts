import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { RunDetailComponent } from './run-detail.component';
import { ApiService, ExperimentRun } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';

class FakeEventSource {
  readonly listeners = new Map<string, Array<(ev: any) => void>>();
  closed = false;
  onerror: ((ev: any) => void) | null = null;

  constructor(readonly url: string) {}

  addEventListener(type: string, cb: (ev: any) => void): void {
    const list = this.listeners.get(type) ?? [];
    list.push(cb);
    this.listeners.set(type, list);
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data: unknown): void {
    for (const cb of this.listeners.get(type) ?? []) {
      cb({ data: JSON.stringify(data) } as MessageEvent);
    }
  }
}

function run(state = 'QUEUED'): ExperimentRun {
  return {
    id: 'run-1',
    templateId: 'QF-001',
    state,
    startedAt: '2026-10-31T00:00:00Z',
    durationMs: 0,
    labels: { clientRequestId: 'cid-1' },
    parameters: { RATE: '10', DUR: '30' },
    metrics: {
      testRunDurationMs: 0,
      iterationPercentiles: { p95: 12 },
      httpPercentiles: {},
      iterationCount: 0,
      rps: 0,
    },
    artifacts: [],
  };
}

function setup(state = 'QUEUED', overrides: Partial<Record<string, any>> = {}) {
  const sse = new FakeEventSource('/api/runs/run-1/events');
  const api = {
    getRun: vi.fn(() => of(run(state))),
    getHints: vi.fn(() => of([])),
    sseEvents: vi.fn(() => sse as unknown as EventSource),
    startRun: vi.fn(() => of(run('RUNNING'))),
    cancelRun: vi.fn(() => of({ id: 'run-1', state: 'CANCELLING' })),
    createRun: vi.fn((_templateId: string, _parameters: Record<string, string>) => of(run('QUEUED'))),
    exportRun: vi.fn(() => of(new Blob(['x']))),
    invalidateRuns: vi.fn(),
    ...overrides,
  };
  const auth = { canOperate: () => true };
  const router = { navigate: vi.fn() };

  TestBed.configureTestingModule({
    imports: [RunDetailComponent],
    providers: [
      provideRouter([]),
      { provide: ApiService, useValue: api },
      { provide: AuthService, useValue: auth },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: 'run-1' }) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(RunDetailComponent);
  fixture.detectChanges();
  const component = fixture.componentInstance as any;
  component['router'] = router;
  return { fixture, component, api, sse, router };
}

describe('RunDetailComponent', () => {
  it('renders live state from named SSE events', () => {
    const { component, sse, fixture } = setup();

    sse.emit('state', { state: 'RUNNING' });
    fixture.detectChanges();

    expect(component.displayState).toBe('RUNNING');
    expect(fixture.nativeElement.textContent).toContain('Live: RUNNING');
  });

  it('appends named log events and bounds the buffer', () => {
    const { component, sse } = setup();

    sse.emit('log', { stream: 'stdout', text: 'k6 running' });
    expect(component.liveLogs).toEqual([
      { level: 'INFO', message: 'k6 running', stream: 'stdout', time: expect.any(String) },
    ]);

    for (let i = 0; i < 250; i++) sse.emit('log', { text: `line ${i}` });
    expect(component.liveLogs.length).toBe(200);
  });

  it('closes the stream and refetches the run on a terminal state', () => {
    const { api, sse } = setup('RUNNING');
    api.getRun.mockClear();

    sse.emit('state', { state: 'SUCCEEDED' });

    expect(sse.closed).toBe(true);
    expect(api.getRun).toHaveBeenCalledWith('run-1');
  });

  it('creates a lab run from measured parameters, never from labels', () => {
    const { component, api, router } = setup('SUCCEEDED');

    component.testInLab();

    const [templateId, parameters] = api.createRun.mock.calls[0] as [string, Record<string, string>];
    expect(templateId).toBe('QF-001');
    expect(parameters['RATE']).toBe('10');
    expect(parameters['DUR']).toBe('30');
    expect(parameters).not.toHaveProperty('clientRequestId');
    expect(parameters['OUT']).toMatch(/^lab-/);
    expect(router.navigate).toHaveBeenCalledWith(['/runs', 'run-1']);
  });

  it('cancels an active run and surfaces the backend state', () => {
    const { component, api } = setup('RUNNING');

    component.cancel();

    expect(api.cancelRun).toHaveBeenCalledWith('run-1');
  });

  it('shows the backend detail when start fails', () => {
    const { component, api, fixture } = setup('QUEUED', {
      startRun: vi.fn(() => throwError(() => ({ error: { detail: 'run is not queued: RUNNING' } }))),
    });

    component.start();
    fixture.detectChanges();

    expect(component.actionError).toBe('run is not queued: RUNNING');
    expect(fixture.nativeElement.textContent).toContain('run is not queued: RUNNING');
  });

  it('hides operator controls for a read-only session', () => {
    const { component } = setup('RUNNING');
    component.auth.canOperate = () => false;
    expect(component.isActive).toBe(true);
  });

  it('does not advertise a live stream for a finished run', () => {
    const { fixture } = setup('FAILED');

    expect(fixture.nativeElement.textContent).not.toContain('Live:');
  });
});
