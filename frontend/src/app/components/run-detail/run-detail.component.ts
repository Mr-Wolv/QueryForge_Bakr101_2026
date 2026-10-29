import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, ExperimentRun, Hint } from '../../services/api.service';
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
        </div>
      </div>
      <ng-template #noHints>
        <app-state-card mode="empty" message="No hints for this run." />
      </ng-template>

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
        <a class="artifact" *ngFor="let a of filteredArtifacts" [href]="'/api/artifacts/' + kindToId(a.kind, a.location)" target="_blank" rel="noopener">
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
export class RunDetailComponent {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  run?: ExperimentRun;
  starting = false;
  hints: Hint[] = [];
  artifactFilter = '';
  filteredArtifacts: ExperimentRun['artifacts'] = [];

  constructor() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.api.getRun(id).subscribe(r => {
      this.run = r;
      this.applyArtifactFilter();
      this.api.getHints(id).subscribe(h => this.hints = h);
      this.cdr.markForCheck();
    });
  }

  start() {
    if (!this.run) return;
    this.starting = true;
    this.api.startRun(this.run.id).subscribe({
      next: updated => this.run = updated,
      error: () => { this.starting = false; }
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

  applyArtifactFilter() {
    if (!this.run) return;
    this.filteredArtifacts = this.artifactFilter
      ? this.run.artifacts.filter(a => a.kind === this.artifactFilter)
      : this.run.artifacts;
  }

  kindToId(kind: string, location: string): string {
    const normalized = location.replace(/\\/g, '/');
    if (kind === 'k6-summary') return normalized.replace('benchmark/results/', '').replace('.json', '');
    if (kind === 'explain-plan') return normalized.replace('docs/experiments/plans/', '').replace('-explain.txt', '');
    return normalized;
  }
}
