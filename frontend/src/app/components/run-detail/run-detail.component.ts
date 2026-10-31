import { Component, inject, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { ApiService, ExperimentRun, Hint, RunArtifact } from '../../services/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { LatencyChartComponent } from '../latency-chart/latency-chart.component';
import { StateCardComponent } from '../state-card/state-card.component';

const TERMINAL_STATES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT']);

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LatencyChartComponent, StateCardComponent],
  template: `
    <ng-template #loadState>
      <section class="detail">
        <app-state-card mode="loading" message="Loading run…" />
      </section>
    </ng-template>
    <section class="detail" *ngIf="run; else loadState">
      <a class="back" routerLink="/runs">&larr; Run history</a>
      <h1>{{run.id}}</h1>
      <div class="meta">
        <span>Template: {{run.templateId}}</span>
        <span>State: <em>{{displayState}}</em></span>
        <span>Duration: {{run.durationMs | number}} ms</span>
        <span>Started: {{run.startedAt | date:'short'}}</span>
      </div>

      <div class="error-banner" *ngIf="actionError" role="alert">{{actionError}}</div>

      <div class="run-controls">
        <button *ngIf="run.state === 'QUEUED' && auth.canOperate()" class="launch" (click)="start()" [disabled]="starting">
          {{starting ? 'Starting…' : 'Start run'}}
        </button>
        <button *ngIf="isActive && auth.canOperate()" class="cancel" (click)="cancel()" [disabled]="cancelling">
          {{cancelling ? 'Cancelling…' : 'Cancel run'}}
        </button>
        <span class="readonly-note" *ngIf="isActive && !auth.canOperate()">
          Read-only account: start and cancel require the operator role.
        </span>
      </div>

      <div class="live" *ngIf="liveState || liveLogs.length > 0">
        <div class="live-head">
          <span class="sse-badge" [class.running]="displayState === 'RUNNING'" [class.succeeded]="displayState === 'SUCCEEDED'" [class.failed]="displayState === 'FAILED'">
            Live: {{ displayState }}
          </span>
          <button class="clear" *ngIf="liveLogs.length > 0" (click)="clearLogs()">Clear log</button>
        </div>
        <pre class="live-log" *ngIf="liveLogs.length > 0" aria-live="polite">{{ liveLogs.join('\n') }}</pre>
      </div>

      <h2>Latency percentiles</h2>
      <app-latency-chart [data]="latencyChartData" />

      <h2>Throughput</h2>
      <div class="throughput">
        <div class="tile">
          <span class="label">RPS</span>
          <span class="value">{{run.metrics.rps | number:'1.2-2'}}</span>
        </div>
        <div class="tile">
          <span class="label">Iterations</span>
          <span class="value">{{run.metrics.iterationCount | number}}</span>
        </div>
        <div class="tile">
          <span class="label">Duration</span>
          <span class="value">{{run.metrics.testRunDurationMs | number}} ms</span>
        </div>
      </div>

      <h2>Optimization hints</h2>
      <div class="hints" *ngIf="hints.length > 0; else noHints">
        <div class="hint" *ngFor="let h of hints" [class.severity]="h.severity">
          <strong>{{h.title}}</strong>
          <p>{{h.body}}</p>
          <button class="test-lab" (click)="testInLab()" *ngIf="auth.canOperate() && (h.severity === 'suggestion' || h.severity === 'warning')" [disabled]="creatingLab">
            {{creatingLab ? 'Creating…' : 'Test in lab'}}
          </button>
        </div>
      </div>
      <ng-template #noHints>
        <p class="hint-empty">No hints for this run.</p>
      </ng-template>

      <div class="actions" *ngIf="run">
        <button class="export" (click)="export('markdown')">Export Markdown</button>
        <button class="export" (click)="export('html')">Export HTML</button>
      </div>

      <h2>Artifacts</h2>
      <div class="filter" *ngIf="run.artifacts.length > 1">
        <label>
          Filter
          <select [(ngModel)]="artifactFilter" (ngModelChange)="applyArtifactFilter()">
            <option value="">All kinds</option>
            <option *ngFor="let k of artifactKinds" [value]="k">{{k}}</option>
          </select>
        </label>
      </div>
      <div class="artifacts" *ngIf="filteredArtifacts.length > 0; else noArtifacts">
        <div class="artifact-row" *ngFor="let a of filteredArtifacts">
          <a class="artifact" [href]="artifactUrl(a)" target="_blank" rel="noopener">
            <span class="kind">{{a.kind}}</span>
            <span class="desc">{{a.description}}</span>
          </a>
          <a class="plan-link" *ngIf="isPlanArtifact(a)" [routerLink]="['/plan', run.id, a.artifactId]">View plan</a>
        </div>
      </div>
      <ng-template #noArtifacts>
        <app-state-card mode="empty" message="No artifacts available." />
      </ng-template>
    </section>
  `,
  styleUrls: ['./run-detail.component.css']
})
export class RunDetailComponent implements OnDestroy {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);
  readonly auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);

  private readonly runId: string;
  run?: ExperimentRun;
  starting = false;
  cancelling = false;
  creatingLab = false;
  actionError: string | null = null;
  hints: Hint[] = [];
  artifactFilter = '';
  filteredArtifacts: ExperimentRun['artifacts'] = [];
  liveState: string | null = null;
  liveLogs: string[] = [];
  private sse?: EventSource;
  private closed = false;

  constructor() {
    this.runId = this.route.snapshot.paramMap.get('id') || '';
    this.load();
    this.connectSse();
  }

  ngOnDestroy(): void {
    this.closed = true;
    this.closeSse();
  }

  get displayState(): string {
    return this.liveState ?? this.run?.state ?? '';
  }

  get isActive(): boolean {
    return !!this.run && !TERMINAL_STATES.has(this.displayState);
  }

  private load() {
    this.api.getRun(this.runId).subscribe({
      next: run => {
        this.run = run;
        this.applyArtifactFilter();
        this.cdr.markForCheck();
      },
      error: () => {
        this.actionError = 'Run could not be loaded. It may have been removed.';
        this.cdr.markForCheck();
      }
    });
    this.api.getHints(this.runId).subscribe({
      next: hints => {
        this.hints = hints;
        // Zoneless app: the hints arrive after the outer markForCheck.
        this.cdr.markForCheck();
      },
      error: () => {
        // Hints are advisory; a failed hint request must not break the run view.
        this.hints = [];
        this.cdr.markForCheck();
      }
    });
  }

  private connectSse() {
    if (!this.runId) return;
    try {
      this.sse = this.api.sseEvents(this.runId);
      // The backend emits NAMED events. `onmessage` only ever fires for unnamed
      // events, so listening that way silently drops every live update.
      this.sse.addEventListener('state', (ev: MessageEvent) => {
        const state = this.parse(ev)?.state;
        if (!state) return;
        this.liveState = state;
        this.cdr.markForCheck();
        if (TERMINAL_STATES.has(state)) {
          this.finishLive();
        }
      });
      this.sse.addEventListener('log', (ev: MessageEvent) => {
        const text = this.parse(ev)?.text;
        if (typeof text !== 'string' || text.length === 0) return;
        this.liveLogs.push(text);
        if (this.liveLogs.length > 200) this.liveLogs.shift();
        this.cdr.markForCheck();
      });
      this.sse.addEventListener('artifact', () => {
        // Artifact metadata lives on the run document; refresh so the list and
        // plan links appear as soon as the runner finalizes them.
        this.refreshRun();
      });
      this.sse.addEventListener('error', (ev: Event) => {
        // Transport failures also arrive as `error`, without data. Only the
        // server-sent named event carries a payload.
        const message = this.parse(ev as MessageEvent)?.message;
        if (typeof message === 'string' && message.length > 0) {
          this.actionError = message;
          this.cdr.markForCheck();
        }
      });
      this.sse.onerror = () => {
        // EventSource reconnects by itself. Once the run is terminal we close it
        // so the page does not reconnect forever against a completed stream.
        if (this.closed || (this.liveState && TERMINAL_STATES.has(this.liveState))) {
          this.closeSse();
        }
      };
    } catch {
      // SSE is an enhancement: the page still renders the persisted run.
    }
  }

  private parse(ev: MessageEvent): any | null {
    if (!ev || typeof ev.data !== 'string') return null;
    try {
      return JSON.parse(ev.data);
    } catch {
      return null;
    }
  }

  private finishLive() {
    this.closeSse();
    this.refreshRun();
  }

  private refreshRun() {
    this.api.getRun(this.runId).subscribe({
      next: run => {
        this.run = run;
        this.applyArtifactFilter();
        this.cdr.markForCheck();
      },
      error: () => {}
    });
  }

  private closeSse() {
    if (this.sse) {
      this.sse.close();
      this.sse = undefined;
    }
  }

  clearLogs() {
    this.liveLogs = [];
    this.cdr.markForCheck();
  }

  start() {
    if (!this.run || this.starting) return;
    this.starting = true;
    this.actionError = null;
    this.api.startRun(this.run.id).subscribe({
      next: updated => {
        this.run = updated;
        this.liveState = updated.state;
        this.starting = false;
        this.cdr.markForCheck();
      },
      error: err => {
        this.starting = false;
        this.actionError = this.describe(err, 'The run could not be started.');
        this.cdr.markForCheck();
      }
    });
  }

  cancel() {
    if (!this.run || this.cancelling) return;
    this.cancelling = true;
    this.actionError = null;
    this.api.cancelRun(this.run.id).subscribe({
      next: () => {
        this.cancelling = false;
        this.refreshRun();
        this.cdr.markForCheck();
      },
      error: err => {
        this.cancelling = false;
        this.actionError = this.describe(err, 'The run could not be cancelled.');
        this.refreshRun();
        this.cdr.markForCheck();
      }
    });
  }

  export(format: 'markdown' | 'html') {
    if (!this.run) return;
    this.api.exportRun(this.run.id, format).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${this.run!.id}.${format === 'html' ? 'html' : 'md'}`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => {
        this.actionError = this.describe(err, 'The export could not be generated.');
        this.cdr.markForCheck();
      }
    });
  }

  testInLab() {
    // Create a candidate run that repeats the measured parameters. `labels`
    // only carries the idempotency key, so it must never be used as input.
    if (!this.run || this.creatingLab) return;
    this.creatingLab = true;
    this.actionError = null;
    const parameters = { ...this.run.parameters, OUT: 'lab-' + Date.now().toString(36) };
    this.api.createRun(this.run.templateId, parameters).subscribe({
      next: created => {
        this.creatingLab = false;
        this.api.invalidateRuns();
        this.router.navigate(['/runs', created.id]);
      },
      error: err => {
        this.creatingLab = false;
        this.actionError = this.describe(err, 'The lab run could not be created.');
        this.cdr.markForCheck();
      }
    });
  }

  private describe(err: any, fallback: string): string {
    const detail = err?.error?.detail ?? err?.error?.title;
    return typeof detail === 'string' && detail.length > 0 ? detail : fallback;
  }

  get latencyChartData() {
    if (!this.run) return [];
    return Object.entries(this.run.metrics.iterationPercentiles).map(([key, value]) => ({ label: key, value: Number(value) }));
  }

  get artifactKinds() {
    if (!this.run) return [];
    const kinds = new Set(this.run.artifacts.map(a => a.kind));
    return Array.from(kinds);
  }

  artifactUrl(a: RunArtifact): string {
    return `/api/runs/${this.run!.id}/artifacts/${encodeURIComponent(a.artifactId)}`;
  }

  isPlanArtifact(a: RunArtifact): boolean {
    return a.kind === 'explain-plan' || a.artifactId.startsWith('explain');
  }

  applyArtifactFilter() {
    if (!this.run) return;
    this.filteredArtifacts = this.artifactFilter
      ? this.run.artifacts.filter(a => a.kind === this.artifactFilter)
      : this.run.artifacts;
  }
}
