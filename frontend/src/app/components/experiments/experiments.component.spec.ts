import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ExperimentsComponent } from './experiments.component';
import { ApiService, ExperimentTemplate } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';

const TEMPLATE: ExperimentTemplate = {
  id: 'QF-001',
  title: 'Filtered search: baseline vs composite index',
  purpose: 'Why is filtered search slow?',
  hypothesis: 'Baseline uses Parallel Seq Scan.',
  parameters: [
    { name: 'RATE', type: 'integer', unit: 'req/s', min: 1, max: 200 },
    { name: 'DUR', type: 'duration', unit: 'seconds', min: 10, max: 600 },
    { name: 'OUT', type: 'string', unit: 'tag' },
  ],
  estimatedDuration: '~3m',
  destructive: false,
  outputs: ['k6 summary JSON'],
  reportPath: 'docs/experiments/QF-001-baseline.md',
  planPaths: [],
};

function setup(rowCount: number | null, overrides: Partial<Record<string, any>> = {}) {
  const api = {
    getExperiments: vi.fn(() => of([TEMPLATE])),
    getReadiness: vi.fn(() => of({
      api: { status: 'UP' },
      database: { status: 'UP', version: '17.0', rowCount: rowCount ?? 0 },
      k6: { status: 'UP', path: 'k6' },
      fingerprint: { appCommit: 'abc', javaVersion: '25', postgresVersion: '17.0', checkedAt: '2026-11-01T00:00:00Z' },
    })),
    createRun: vi.fn(() => of({ id: 'run-1' })),
    startRun: vi.fn(() => of({ id: 'run-1' })),
    ...overrides,
  };
  TestBed.configureTestingModule({
    imports: [ExperimentsComponent],
    providers: [
      provideRouter([]),
      { provide: ApiService, useValue: api },
      {
        provide: AuthService,
        useValue: {
          session: signal({ authEnabled: false, authenticated: false, operator: true, displayName: '' }),
          canOperate: () => true,
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(ExperimentsComponent);
  fixture.detectChanges();
  return { fixture, api, component: fixture.componentInstance };
}

describe('ExperimentsComponent', () => {
  it('disables launching and explains why when the dataset is empty', () => {
    const { fixture, api, component } = setup(0);

    expect(component.datasetEmpty).toBe(true);
    const launch = fixture.nativeElement.querySelector('button.launch') as HTMLButtonElement;
    expect(launch.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('The product dataset is empty (0 rows).');

    component.launch('QF-001');
    expect(api.createRun).not.toHaveBeenCalled();
    expect(component.launchError).toContain('dataset is empty');
  });

  it('allows launching once the dataset is populated', () => {
    const { fixture, component } = setup(250);

    expect(component.datasetEmpty).toBe(false);
    const launch = fixture.nativeElement.querySelector('button.launch') as HTMLButtonElement;
    expect(launch.disabled).toBe(false);
    expect(fixture.nativeElement.textContent).not.toContain('The product dataset is empty');
  });
});
