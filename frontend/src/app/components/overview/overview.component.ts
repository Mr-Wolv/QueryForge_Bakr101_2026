import { Component, inject } from '@angular/core';
import { CommonModule, AsyncPipe } from '@angular/common';
import { map } from 'rxjs/operators';
import { ApiService, Readiness, ExperimentRun } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';

@Component({
  selector: 'app-overview',
  standalone: true,
  imports: [CommonModule, AsyncPipe, StateCardComponent],
  template: `
    <section class="overview">
      <h1>QueryForge</h1>
      <p class="subtitle">PostgreSQL performance evidence browser</p>

      <div class="grid">
        <div class="card" *ngIf="readiness | async as r; else loadEnv">
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
        <ng-template #loadEnv>
          <app-state-card mode="loading" message="Loading environment…" />
        </ng-template>

        <div class="card" *ngIf="latestRun | async as latestRun; else loadLatest">
          <h2>Latest run</h2>
          <p><strong>{{ $any(latestRun).id }}</strong></p>
          <p>Template: {{ $any(latestRun).templateId }}</p>
          <p>State: {{ $any(latestRun).state }}</p>
          <p>p95: {{ $any(latestRun).metrics.iterationPercentiles['p95'] | number:'1.0-0' }} ms</p>
          <p>RPS: {{ $any(latestRun).metrics.rps | number:'1.2-2' }}</p>
        </div>
        <ng-template #loadLatest>
          <app-state-card mode="empty" message="No runs yet." />
        </ng-template>

        <div class="card" *ngIf="baselineRun | async as base; else loadBase">
          <h2>Baseline (QF-001)</h2>
          <p><strong>{{ $any(base).id }}</strong></p>
          <p>p95: {{ $any(base).metrics.iterationPercentiles['p95'] | number:'1.0-0' }} ms</p>
          <p>p50: {{ $any(base).metrics.iterationPercentiles['p50'] | number:'1.0-0' }} ms</p>
        </div>
        <ng-template #loadBase>
          <app-state-card mode="unavailable" message="Baseline not found." />
        </ng-template>
      </div>
    </section>
  `,
  styleUrls: ['./overview.component.css']
})
export class OverviewComponent {
  private api = inject(ApiService);
  readiness = this.api.getReadiness();
  latestRun = this.api.getRuns().pipe(map((runs: ExperimentRun[]) => runs[runs.length - 1] ?? null));
  baselineRun = this.api.getRuns().pipe(map((runs: ExperimentRun[]) => runs.find((r: ExperimentRun) => r.id === 'qf001-baseline-search-1M-r10') ?? null));
}
