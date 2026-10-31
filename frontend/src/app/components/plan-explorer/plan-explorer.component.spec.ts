import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { PlanExplorerComponent } from './plan-explorer.component';
import { ApiService } from '../../services/api.service';

const PLAN = {
  type: 'Root',
  properties: {},
  raw: '',
  children: [
    {
      type: 'Nested Loop',
      properties: {},
      raw: '',
      children: [{ type: 'Seq Scan', properties: {}, raw: '', children: [] }],
    },
  ],
};

describe('PlanExplorerComponent', () => {
  it('loads the plan through the run-scoped endpoint', () => {
    const api = { getParsedPlan: vi.fn(() => of(PLAN)) };
    TestBed.configureTestingModule({
      imports: [PlanExplorerComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });

    const fixture = TestBed.createComponent(PlanExplorerComponent);
    fixture.componentRef.setInput('runId', 'run-1');
    fixture.componentRef.setInput('artifactId', 'explain-plan');
    fixture.detectChanges();

    expect(api.getParsedPlan).toHaveBeenCalledWith('explain-plan', 'run-1');
    expect(fixture.componentInstance.nodeCount).toBe(3);
    expect(fixture.componentInstance.hasError).toBe(false);
  });

  it('reports a missing plan instead of rendering an empty tree', () => {
    const api = { getParsedPlan: vi.fn(() => throwError(() => ({ status: 404 }))) };
    TestBed.configureTestingModule({
      imports: [PlanExplorerComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });

    const fixture = TestBed.createComponent(PlanExplorerComponent);
    fixture.componentRef.setInput('artifactId', 'missing-plan');
    fixture.detectChanges();

    expect(fixture.componentInstance.hasError).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('could not be loaded');
    expect(api.getParsedPlan).toHaveBeenCalledWith('missing-plan', undefined);
  });
});
