import { Component, inject, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink, Router } from '@angular/router';
import { ApiService, ExperimentRun, Hint, RunEvent } from '../../services/api.service';
import { LatencyChartComponent } from '../latency-chart/latency-chart.component';
import { StateCardComponent } from '../state-card/state-card.component';

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
        <span>State: <em>{{run.state}}</em></span>
        <span>Duration: {{run.durationMs | number}} ms</span>
        <span>Started: {{run.startedAt | date:'short'}}</span>
      </div>

      <button *ngIf="run.state === 'QUEUED'" class="launch" (click)="start()" [disabled]="starting">
        {{starting ? 'Starting…' : 'Start run'}}
      </button>

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
          <button class="test-lab" (click)="testInLab(h.title)" *ngIf="h.severity === 'suggestion' || h.severity === 'warning'">
            Test in lab
          </button>
        </div>
      </div>
      <ng-template #noHints>
          <app-state-card mode="empty" message="No hints for this run." />
      </ng-template>

      <div class="actions" *ngIf="run">
        <button class="export" (click)="export('markdown')">Export Markdown</button>
        <button class="export" (click)="export('html')">Export HTML</button>
      </div>

      <div class="sse-log" *ngIf="liveState">
        <span class="sse-badge" [class.running]="liveState === 'RUNNING'" [class.succeeded]="liveState === 'SUCCEEDED'" [class.failed]="liveState === 'FAILED'">
          Live: {{ liveState }}
        </span>
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
        <a class="artifact" *ngFor="let a of filteredArtifacts" [href]="artifactUrl(a)" target="_blank" rel="noopener">
          <span class="kind">{{a.kind}}</span>
          <span class="desc">{{a.description}}</span>
        </a>
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
  private cdr = inject(ChangeDetectorRef);
  private router = inject(Router);
  run?: ExperimentRun;
  starting = false;
  hints: Hint[] = [];
  artifactFilter = '';
  filteredArtifacts: ExperimentRun['artifacts'] = [];
  liveState: string | null = null;
  private sse?: EventSource;

  constructor() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.api.getRun(id).subscribe(r => {
      this.run = r;
      this.applyArtifactFilter();
      this.api.getHints(id).subscribe(h => this.hints = h);
      this.cdr.markForCheck();
    });
    this.connectSse(id);
  }

  ngOnDestroy(): void {
    if (this.sse) {
      this.sse.close();
    }
  }

  private connectSse(id: string) {
    try {
      this.sse = this.api.sseEvents(id);
      this.sse.onmessage = (ev) => {
        try {
          const data: RunEvent = JSON.parse(ev.data);
          this.liveState = data.state;
          this.cdr.markForCheck();
        } catch {}
      };
      this.sse.onerror = () => {
        this.sse?.close();
      };
    } catch {}
  }

  start() {
    if (!this.run) return;
    this.starting = true;
    this.api.startRun(this.run.id).subscribe({
      next: updated => { this.run = updated; this.starting = false; this.cdr.markForCheck(); },
      error: () => { this.starting = false; }
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
      error: () => {}
    });
  }

  testInLab(hintTitle: string) {
    // Phase 4: create a candidate run to test the suggested optimization
    if (!this.run) return;
    this.api.createRun(this.run.templateId, {
      ...Object.fromEntries(Object.entries(this.run.labels)),
      OUT: 'lab-' + Date.now().toString(36)
    }).subscribe({
      next: (run) => { this.router.navigate(['/runs', run.id]); },
      error: () => {}
    });
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

  artifactUrl(a: { location: string }): string {
    const name = a.location.split(/[\\/]/).pop()?.replace(/\.[^.]+$/, '') ?? '';
    return `/api/runs/${this.run!.id}/artifacts/${name}`;
  }

  applyArtifactFilter() {
    if (!this.run) return;
    this.filteredArtifacts = this.artifactFilter
      ? this.run.artifacts.filter(a => a.kind === this.artifactFilter)
      : this.run.artifacts;
  }
}
