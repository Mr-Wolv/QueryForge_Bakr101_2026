import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, ExperimentRun, ComparisonResult, MetricDeltaValue } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { StateCardComponent } from '../state-card/state-card.component';
import { catchError, forkJoin, map, of, Observable } from 'rxjs';

interface DeltaRow {
  key: string;
  label: string;
  group: string;
  left: string;
  right: string;
  delta: string;
  percent: string;
  direction: 'better' | 'worse' | 'neutral';
}

interface CandidateComparison {
  candidateId: string;
  result?: ComparisonResult;
  rows: DeltaRow[];
  error?: string;
}

const LABELS: Record<string, { label: string; group: string; higherIsBetter: boolean }> = {
  'iteration.p50': { label: 'p50', group: 'Iteration latency', higherIsBetter: false },
  'iteration.p95': { label: 'p95', group: 'Iteration latency', higherIsBetter: false },
  'iteration.p99': { label: 'p99', group: 'Iteration latency', higherIsBetter: false },
  'iteration.avg': { label: 'average', group: 'Iteration latency', higherIsBetter: false },
  'http.p50': { label: 'p50', group: 'HTTP latency', higherIsBetter: false },
  'http.p95': { label: 'p95', group: 'HTTP latency', higherIsBetter: false },
  'http.p99': { label: 'p99', group: 'HTTP latency', higherIsBetter: false },
  'http.avg': { label: 'average', group: 'HTTP latency', higherIsBetter: false },
  rps: { label: 'Throughput', group: 'Load', higherIsBetter: true },
  iterations: { label: 'Iterations', group: 'Load', higherIsBetter: true },
  testRunDurationMs: { label: 'Test run duration', group: 'Load', higherIsBetter: false },
};

@Component({
  selector: 'app-compare',
  standalone: true,
  imports: [CommonModule, FormsModule, StateCardComponent],
  template: `
    <section class="compare">
      <h1>Compare runs</h1>
      <form (submit)="compare()">
        <label>
          Baseline run
          <select [(ngModel)]="a" name="a" required>
            <option value="">Select run</option>
            <option *ngFor="let r of runs" [value]="r.id">{{r.id}}</option>
          </select>
        </label>
        <label>
          Candidate runs
          <select [(ngModel)]="candidates" name="candidates" multiple required size="5">
            <option *ngFor="let r of runs" [value]="r.id" [disabled]="r.id === a">{{r.id}}</option>
          </select>
        </label>
        <button *ngIf="auth.canOperate()" type="submit" [disabled]="loading || candidates.length === 0">
          {{loading ? 'Comparing…' : 'Compare with candidates'}}
        </button>
        <p *ngIf="!auth.canOperate()" class="read-only-note">An experiment operator must create comparisons.</p>
      </form>

      <app-state-card *ngIf="error" mode="unavailable" [message]="error" />

      <section class="batch-summary" *ngIf="batchResults.length > 0">
        <h2>Baseline against candidates</h2>
        <table>
          <thead>
            <tr><th scope="col">Candidate</th><th scope="col">Compatible</th><th scope="col">p95 change</th><th scope="col">Details</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of batchResults">
              <th scope="row" class="mono">{{item.candidateId}}</th>
              <td *ngIf="item.result">{{item.result.verdict.compatible ? 'Yes' : 'No'}}</td>
              <td *ngIf="!item.result" colspan="2" role="alert">{{item.error}}</td>
              <td *ngIf="item.result">
                {{item.result.metricsDelta?.['iteration.p95']?.delta ?? 'n/a'}}
                {{item.result.metricsDelta?.['iteration.p95']?.unit ?? ''}}
              </td>
              <td><button class="small" type="button" (click)="selectBatchResult(item)" [disabled]="!item.result">View</button></td>
            </tr>
          </tbody>
        </table>
      </section>

      <div class="result" *ngIf="result">
        <h2 class="title">
          <span class="run">{{result.baseline}}</span>
          <span class="vs">vs</span>
          <span class="run">{{result.candidate}}</span>
        </h2>
        <div class="verdict" [class.pass]="result.verdict.compatible" [class.fail]="!result.verdict.compatible">
          <span class="badge">
            {{result.verdict.compatible ? 'COMPATIBLE' : 'INCOMPATIBLE'}}
          </span>
          <p class="verdict-note">
            {{result.verdict.compatible
              ? 'Both runs share the same template, parameters and environment — deltas are a valid A/B result.'
              : 'These runs differ in ways that make a controlled comparison unsafe. Deltas are shown for diagnosis only.'}}
          </p>
        </div>

        <div class="blockers" *ngIf="result.verdict.blockers?.length">
          <h3>Blockers</h3>
          <ul>
            <li *ngFor="let b of result.verdict.blockers">{{b}}</li>
          </ul>
        </div>

        <div class="warnings-list" *ngIf="result.verdict.warnings?.length">
          <h3>Warnings</h3>
          <ul>
            <li *ngFor="let w of result.verdict.warnings">{{w}}</li>
          </ul>
        </div>

        <div class="deltas" *ngIf="rows.length">
          <div class="deltas-head">
            <h2>Metric deltas</h2>
            <p class="legend"><span class="swatch better"></span>improvement <span class="swatch worse"></span>regression</p>
          </div>
          <table>
            <thead>
              <tr>
                <th scope="col">Metric</th>
                <th scope="col" class="num">Baseline</th>
                <th scope="col" class="num">Candidate</th>
                <th scope="col" class="num">Change</th>
              </tr>
            </thead>
            <tbody>
              <ng-container *ngFor="let row of rows">
                <tr class="group-head" *ngIf="row.key === groupStart(row)">
                  <th scope="rowgroup" colspan="4">{{row.group}}</th>
                </tr>
                <tr>
                  <th scope="row">{{row.label}}</th>
                  <td class="num">{{row.left}}</td>
                  <td class="num">{{row.right}}</td>
                  <td class="num delta" [ngClass]="row.direction">
                    <span class="abs">{{row.delta}}</span>
                    <span class="pct" *ngIf="row.percent">{{row.percent}}</span>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </div>
      </div>

      <div class="raw" *ngIf="!result && !error">
        <app-state-card mode="empty" message="Select two runs to compare." />
      </div>

      <section class="history">
        <h2>Comparison history</h2>
        <div *ngIf="loadingHistory" class="loading">Loading history…</div>
        <table *ngIf="!loadingHistory && history.length > 0">
          <thead>
            <tr>
              <th scope="col">Report ID</th>
              <th scope="col">Left</th>
              <th scope="col">Right</th>
              <th scope="col">Compatible</th>
              <th scope="col">Created at</th>
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let h of history">
              <td class="mono">{{h.id}}</td>
              <td class="mono">{{h.leftRunId}}</td>
              <td class="mono">{{h.rightRunId}}</td>
              <td>
                <span [class.pass]="h.verdict.compatible" [class.fail]="!h.verdict.compatible">
                  {{h.verdict.compatible ? 'Yes' : 'No'}}
                </span>
              </td>
              <td>{{h.createdAt | date:'short'}}</td>
              <td>
                <button class="small" (click)="viewComparison(h.id)">View</button>
                <button class="small" (click)="exportComparison(h.id, 'markdown')">Export MD</button>
                <button class="small" (click)="exportComparison(h.id, 'html')">Export HTML</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p *ngIf="!loadingHistory && history.length === 0">No comparisons yet.</p>
      </section>
    </section>
  `,
  styleUrls: ['./compare.component.css']
})
export class CompareComponent {
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  runs: ExperimentRun[] = [];
  result?: ComparisonResult;
  history: ComparisonResult[] = [];
  batchResults: CandidateComparison[] = [];
  rows: DeltaRow[] = [];
  loading = false;
  loadingHistory = false;
  error?: string;
  a = '';
  candidates: string[] = [];

  constructor() {
    this.api.getRuns().subscribe({
      next: r => { this.runs = r.content; this.cdr.markForCheck(); },
      error: () => {
        this.error = 'Run history could not be loaded, so nothing can be compared yet.';
        this.cdr.markForCheck();
      }
    });
    this.loadHistory();
  }

  loadHistory() {
    this.loadingHistory = true;
    this.api.getComparisons().subscribe({
      next: (list) => {
        this.history = list;
        this.loadingHistory = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingHistory = false;
        this.cdr.markForCheck();
      }
    });
  }

  viewComparison(id: string) {
    this.api.getComparison(id).subscribe({
      next: (res) => {
        this.result = {
          ...res,
          baseline: res.leftRunId,
          candidate: res.rightRunId,
        };
        this.rows = this.buildRows(res.metricsDelta ?? {});
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'Could not load this comparison.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  exportComparison(id: string, format: 'markdown' | 'html' = 'markdown') {
    this.api.exportComparison(id, format).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${id}.${format}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.error = 'Export failed.';
        this.cdr.markForCheck();
      }
    });
  }

  groupStart(row: DeltaRow): string {
    const index = this.rows.indexOf(row);
    const previous = index > 0 ? this.rows[index - 1] : undefined;
    return !previous || previous.group !== row.group ? row.key : '';
  }

  compare() {
    const leftRunId = this.a;
    const candidateIds = [...new Set(this.candidates.filter(id => id && id !== leftRunId))];
    if (!leftRunId || candidateIds.length === 0) return;
    this.loading = true;
    this.error = undefined;
    this.result = undefined;
    this.rows = [];
    this.batchResults = [];
    const requests: Observable<CandidateComparison>[] = candidateIds.map(candidateId =>
      this.api.compare(leftRunId, candidateId).pipe(
        map(res => ({
          candidateId,
          result: { ...res, baseline: res.leftRunId, candidate: res.rightRunId },
          rows: this.buildRows(res.metricsDelta ?? {}),
        })),
        catchError(err => of({
          candidateId,
          rows: [],
          error: this.describe(err),
        }))
      )
    );
    forkJoin(requests).subscribe({
      next: results => {
        this.batchResults = results;
        const firstSuccess = results.find(item => item.result);
        if (firstSuccess?.result) {
          this.result = firstSuccess.result;
          this.rows = firstSuccess.rows;
        }
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.error = 'The comparison requests could not be completed.';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  selectBatchResult(item: CandidateComparison) {
    if (!item.result) return;
    this.result = item.result;
    this.rows = item.rows;
    this.cdr.markForCheck();
  }

  private describe(err: any): string {
    const detail = err?.error?.detail ?? err?.error?.title;
    return typeof detail === 'string' && detail.length > 0
      ? detail
      : 'This candidate could not be compared.';
  }

  private buildRows(deltas: Record<string, MetricDeltaValue>): DeltaRow[] {
    const groupRank = ['Iteration latency', 'HTTP latency', 'Load', 'Other'];
    const rank = (group: string) => {
      const index = groupRank.indexOf(group);
      return index === -1 ? groupRank.length : index;
    };
    return Object.entries(deltas).map(([key, value]) => {
      const meta = LABELS[key] ?? { label: key, group: 'Other', higherIsBetter: false };
      const direction: DeltaRow['direction'] =
        value.delta === 0 ? 'neutral'
          : meta.higherIsBetter === value.delta > 0 ? 'better'
            : 'worse';
      const negative = value.delta < 0;
      return {
        key,
        label: value.unit === 'ms' ? `${meta.label} (ms)` : meta.label,
        group: meta.group,
        left: this.formatValue(value.left, value.unit),
        right: this.formatValue(value.right, value.unit),
        delta: `${negative ? '−' : '+'}${this.formatValue(Math.abs(value.delta), value.unit, false)}`,
        percent: value.deltaPercent === null || value.deltaPercent === undefined
          ? ''
          : `${value.deltaPercent > 0 ? '+' : negative ? '−' : ''}${Math.abs(value.deltaPercent).toFixed(1)}%`,
        direction,
      };
    }).sort((a, b) => rank(a.group) - rank(b.group));
  }

  private formatValue(value: number, unit: string, withUnit = true): string {
    const decimals = unit === 'req/s' ? 2 : 0;
    const formatted = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    }).format(value);
    return withUnit && unit === 'req/s' ? `${formatted} req/s` : formatted;
  }
}
