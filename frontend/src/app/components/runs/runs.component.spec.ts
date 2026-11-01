import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { RunsComponent } from './runs.component';
import { ApiService } from '../../services/api.service';

describe('RunsComponent', () => {
  it('shows a retryable error instead of an endless loading state', () => {
    const api = {
      getRuns: vi.fn(() => throwError(() => ({ status: 503 }))),
      getExperiments: vi.fn(() => of([])),
    };
    TestBed.configureTestingModule({
      imports: [RunsComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });

    const fixture = TestBed.createComponent(RunsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.componentInstance.loadError).toContain('could not be loaded');
    expect(fixture.nativeElement.textContent).toContain('Retry');
  });

  it('renders an empty state when the history is genuinely empty', () => {
    const api = {
      getRuns: vi.fn(() => of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false })),
      getExperiments: vi.fn(() => of([])),
    };
    TestBed.configureTestingModule({
      imports: [RunsComponent],
      providers: [
        provideRouter([]),
        { provide: ApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });

    const fixture = TestBed.createComponent(RunsComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.loading).toBe(false);
    expect(fixture.componentInstance.loadError).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('No runs recorded yet.');
  });
});
