import { Component, ChangeDetectorRef, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService, ExperimentRun, Readiness } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { StateCardComponent } from '../state-card/state-card.component';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, RouterLink, StateCardComponent],
  template: `
    <section class="overview">
      <header class="page-intro">
        <p class="eyebrow">POSTGRESQL PERFORMANCE WORKSPACE</p>
        <h1>QueryForge</h1>
        <p class="subtitle">Turn database questions into evidence you can inspect, compare, and trust.</p>
      </header>

      <section class="first-session" *ngIf="signedOut">
        <div class="first-session-copy">
          <p class="eyebrow">AUTHENTICATED WORKSPACE</p>
          <h2>Sign in to open your experiment history.</h2>
          <p>
            This deployment requires an account. Use <strong>Sign in</strong> in the header.
            Read-only accounts can inspect runs; operator accounts can also start and cancel them.
          </p>
        </div>
      </section>

      <ng-container *ngIf="!signedOut">

      <section class="first-session" *ngIf="showFirstSession">
        <div class="first-session-copy">
          <p class="eyebrow">{{ runsError ? 'WORKSPACE CONNECTION' : 'YOUR FIRST SESSION' }}</p>
          <h2>{{ runsError ? 'Your workspace is waiting to connect.' : 'Start with a question. Leave with evidence.' }}</h2>
          <p *ngIf="runsError" class="connection-error" role="status">
            {{ runsError }} Your experiment history will appear here once the API is reachable.
          </p>
          <button *ngIf="runsError" type="button" class="retry-button" (click)="loadRuns()">Retry history</button>
          <p *ngIf="!runsError">
            Pick a prepared PostgreSQL experiment, tune its workload, and launch it. QueryForge keeps the run,
            measurements, and query-plan evidence together.
          </p>
          <div class="first-session-actions">
            <a class="primary-action" routerLink="/experiments">
              Explore experiments
              <svg viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <path d="M3.5 10h12m-5-5 5 5-5 5" />
              </svg>
            </a>
            <a class="secondary-action" routerLink="/runs">Browse run history</a>
          </div>
        </div>

        <div class="first-session-guide" aria-label="How to get started">
          <p class="guide-heading">A useful first question</p>
          <a class="question-link" routerLink="/experiments" fragment="qf-001">
            <span class="question-id">QF-001</span>
            <span>
              <strong>What does a workload-shaped index buy?</strong>
              <small>Compare filtered search before and after a composite index.</small>
            </span>
            <span class="question-arrow" aria-hidden="true">↗</span>
          </a>
          <ol class="steps">
            <li><span>01</span><span>Choose an experiment and adjust its workload.</span></li>
            <li><span>02</span><span>Run it against your configured environment.</span></li>
            <li><span>03</span><span>Inspect metrics, plans, and comparisons.</span></li>
          </ol>
        </div>
      </section>

      <div class="environment-status" *ngIf="readinessLoading" role="status">
        <span class="status-mark loading-mark" aria-hidden="true"></span>
        Checking the experiment environment…
      </div>
      <div class="environment-status unavailable" *ngIf="!readinessLoading && readinessError" role="status">
        <span class="status-mark" aria-hidden="true"></span>
        <span>{{ readinessError }}</span>
        <button type="button" (click)="loadReadiness()">Retry connection</button>
      </div>
      <div class="environment-status" *ngIf="!readinessLoading && readiness as r">
        <span class="status-mark" [class.ready-mark]="r.api.status === 'UP' && r.database.status === 'UP' && r.k6.status === 'UP'" aria-hidden="true"></span>
        Environment · API {{r.api.status}} · PostgreSQL {{r.database.version}} · k6 {{r.k6.status}}
      </div>

      <div class="grid" *ngIf="!showFirstSession && runsLoading">
        <app-state-card mode="loading" message="Loading experiment history…" />
      </div>
      <div class="grid" *ngIf="!showFirstSession && runsError">
        <app-state-card mode="unavailable" [message]="runsError">
          <button type="button" class="retry-button" (click)="loadRuns()">Retry history</button>
        </app-state-card>
      </div>
      <div class="grid" *ngIf="!showFirstSession && !runsLoading && !runsError && runs.length > 0">
        <div class="card" *ngIf="readiness as r; else readinessCard">
          <h2>Environment</h2>
          <dl>
            <dt>API</dt><dd [class.ok]="r.api.status==='UP'">{{r.api.status}}</dd>
            <dt>Database</dt><dd [class.ok]="r.database.status==='UP'">{{r.database.status}} · {{r.database.version}} · {{r.database.rowCount | number}} rows</dd>
            <dt>k6</dt><dd [class.ok]="r.k6.status==='UP'">{{r.k6.status}}</dd>
            <dt>Java</dt><dd>{{r.fingerprint.javaVersion}}</dd>
            <dt>PostgreSQL</dt><dd>{{r.fingerprint.postgresVersion}}</dd>
            <dt>Checked</dt><dd>{{r.fingerprint.checkedAt | date:'short'}}</dd>
          </dl>
        </div>
        <ng-template #readinessCard>
          <div class="card">
            <h2>Environment</h2>
            <p class="card-message">{{readinessError || 'Checking environment readiness…'}}</p>
            <button *ngIf="readinessError" type="button" class="retry-button" (click)="loadReadiness()">Retry connection</button>
          </div>
        </ng-template>

        <div class="card">
          <h2>Latest run</h2>
          <p><strong>{{latestRun?.id}}</strong></p>
          <p>Template: {{latestRun?.templateId}}</p>
          <p>State: {{latestRun?.state}}</p>
          <p>p95: {{metricText(latestRun?.metrics?.iterationPercentiles?.['p95'], 0, 'ms')}}</p>
          <p>RPS: {{metricText(latestRun?.metrics?.rps, 2)}}</p>
          <a class="card-link" *ngIf="latestRun" [routerLink]="['/runs', latestRun.id]">Open latest run</a>
        </div>

        <div class="card">
          <h2>QF-001 · latest</h2>
          <ng-container *ngIf="baselineRun as base; else noBaseline">
            <p><strong>{{base.id}}</strong></p>
            <p>p95: {{metricText(base.metrics?.iterationPercentiles?.['p95'], 0, 'ms')}}</p>
            <p>p50: {{metricText(base.metrics?.iterationPercentiles?.['p50'], 0, 'ms')}}</p>
            <a class="card-link" [routerLink]="['/runs', base.id]">Inspect run</a>
          </ng-container>
          <ng-template #noBaseline><p class="card-message">No QF-001 run is recorded yet.</p></ng-template>
        </div>
      </div>
      </ng-container>
    </section>
  `,
  styleUrls: ['./overview.component.css']
})
export class OverviewComponent {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  runs: ExperimentRun[] = [];
  readiness: Readiness | null = null;
  runsLoading = true;
  readinessLoading = true;
  runsError = '';
  readinessError = '';
  signedOut = false;
  private loaded = false;

  get showFirstSession(): boolean {
    return !this.runsLoading && (this.runsError.length > 0 || this.runs.length === 0);
  }

  get latestRun(): ExperimentRun | null {
    return this.mostRecent(this.runs);
  }

  get baselineRun(): ExperimentRun | null {
    const qf001 = this.runs.filter(run => run.templateId === 'QF-001');
    if (qf001.length === 0) return null;
    // Legacy fixtures tag the baseline in the id; auto-generated runs are
    // `qf-001-run-<hex>`, so fall back to the most recent QF-001 run.
    return qf001.find(run => run.id.includes('baseline')) ?? this.mostRecent(qf001);
  }

  /**
   * The API returns the newest run first, so "latest" must be derived from
   * `startedAt` rather than assumed to be the last element of the page.
   */
  private mostRecent(runs: ExperimentRun[]): ExperimentRun | null {
    if (runs.length === 0) return null;
    return runs.reduce((newest, candidate) =>
      new Date(candidate.startedAt).getTime() > new Date(newest.startedAt).getTime() ? candidate : newest
    );
  }

  /**
   * The `number` pipe renders nothing for a missing value, which left a bare
   * unit behind ("p95: ms"). Show an em dash instead.
   */
  metricText(value: number | null | undefined, digits: number, unit: string = ''): string {
    if (value === null || value === undefined || !Number.isFinite(value)) return '—';
    const formatted = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(value);
    return unit ? `${formatted} ${unit}` : formatted;
  }

  constructor() {
    // Wait for the session before touching protected endpoints. Firing them
    // anonymously produced a guaranteed 401 on every signed-out visit, which
    // the interceptor then turned into a forced OIDC redirect.
    effect(() => {
      const session = this.auth.session();
      if (session === null) return;

      if (session.authEnabled && !session.authenticated) {
        this.signedOut = true;
        this.loaded = false;
        this.runs = [];
        this.readiness = null;
        this.runsLoading = false;
        this.readinessLoading = false;
        this.cdr.markForCheck();
        return;
      }

      this.signedOut = false;
      if (!this.loaded) {
        this.loaded = true;
        this.loadRuns();
        this.loadReadiness();
      }
    });
  }

  loadRuns(): void {
    this.runsLoading = true;
    this.runsError = '';
    this.api.getRuns().subscribe({
      next: runs => {
        this.runs = runs.content;
        this.runsLoading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.runsError = this.errorMessage(error, 'Could not load experiment history.');
        this.runsLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadReadiness(): void {
    this.readinessLoading = true;
    this.readinessError = '';
    this.api.getReadiness().subscribe({
      next: readiness => {
        this.readiness = readiness;
        this.readinessLoading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.readinessError = this.errorMessage(error, 'Could not check the experiment environment.');
        this.readinessLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null) {
      if ('status' in error && typeof error.status === 'number' && [0, 502, 503, 504].includes(error.status)) {
        return `API unavailable (HTTP ${error.status}). Start the backend and try again.`;
      }
      const body = (error as any).error;
      if (body && typeof body === 'object' && typeof body.detail === 'string') {
        return body.detail;
      }
      if ('detail' in error && typeof (error as any).detail === 'string') {
        return (error as any).detail;
      }
    }
    return fallback;
  }
}
