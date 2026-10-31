import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService, ExperimentRun } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';
import { SkeletonCardComponent } from '../skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-runs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StateCardComponent, SkeletonCardComponent],
  template: `
    <section class="runs">
      <h1>Run history</h1>
      <app-skeleton-card mode="loading" *ngIf="loading" />
      <div class="error-banner" *ngIf="loadError" role="alert">
        {{ loadError }}
        <button class="retry" (click)="reload()">Retry</button>
      </div>
      <div class="filter" *ngIf="!loading && !loadError && runs.length > 0">
        <label>
          Filter by template
          <select [(ngModel)]="templateFilter" (ngModelChange)="applyFilter()">
            <option value="">All templates</option>
            <option *ngFor="let t of templates" [value]="t">{{t}}</option>
          </select>
        </label>
        <label>
          Filter by state
          <select [(ngModel)]="stateFilter" (ngModelChange)="applyFilter()">
            <option value="">All states</option>
            <option *ngFor="let s of states" [value]="s">{{s}}</option>
          </select>
        </label>
        <label>
          From date
          <input type="date" [(ngModel)]="dateFrom" (ngModelChange)="applyFilter()" />
        </label>
        <label>
          To date
          <input type="date" [(ngModel)]="dateTo" (ngModelChange)="applyFilter()" />
        </label>
      </div>
      <div class="list" *ngIf="!loadError && filteredRuns.length > 0; else empty">
        <div class="list-head" aria-hidden="true">
          <span>Run</span>
          <span>Template</span>
          <span>State</span>
          <span class="num">p95 latency</span>
          <span class="num">Throughput</span>
        </div>
        <a class="row" *ngFor="let r of filteredRuns" [routerLink]="['/runs', r.id]">
          <span class="id">{{r.id}}</span>
          <span class="template">{{r.templateId}}</span>
          <span class="state" [class]="stateClass(r.state)">{{r.state}}</span>
          <span class="p95">{{p95(r)}}</span>
          <span class="rps">{{rps(r)}}</span>
        </a>
      </div>
      <ng-template #empty>
        <app-state-card *ngIf="!loadError" mode="empty" message="No runs recorded yet." />
      </ng-template>
    </section>
  `,
  styleUrls: ['./runs.component.css']
})
export class RunsComponent {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  private route = inject(ActivatedRoute);
  runs: ExperimentRun[] = [];
  filteredRuns: ExperimentRun[] = [];
  templateFilter = '';
  stateFilter = '';
  dateFrom = '';
  dateTo = '';
  loading = true;
  loadError: string | null = null;

  get templates(): string[] {
    const set = new Set(this.runs.map(r => r.templateId));
    return Array.from(set);
  }

  get states(): string[] {
    const set = new Set(this.runs.map(r => r.state));
    return Array.from(set);
  }

  constructor() {
    // The catalog links here with the template preselected.
    this.templateFilter = this.route.snapshot.queryParamMap.get('template') ?? '';
    this.reload();
  }

  reload() {
    this.loading = true;
    this.loadError = null;
    this.api.getRuns().subscribe({
      next: r => {
        this.runs = r;
        this.loading = false;
        this.applyFilter();
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.loadError = 'Run history could not be loaded. The backend may be unavailable.';
        this.cdr.markForCheck();
      }
    });
  }

  stateClass(state: string): string {
    return 'state ' + state.toLowerCase();
  }

  p95(run: ExperimentRun): string {
    const value = run.metrics?.iterationPercentiles?.['p95'];
    return value == null ? '—' : `${Math.round(Number(value)).toLocaleString('en-US')} ms`;
  }

  rps(run: ExperimentRun): string {
    const value = run.metrics?.rps;
    return value == null || value === 0 ? '—' : `${Number(value).toFixed(2)} RPS`;
  }

  applyFilter() {
    this.filteredRuns = this.runs.filter(r => {
      if (this.templateFilter && r.templateId !== this.templateFilter) return false;
      if (this.stateFilter && r.state !== this.stateFilter) return false;
      if (this.dateFrom && r.startedAt) {
        const runDate = new Date(r.startedAt).toISOString().slice(0, 10);
        if (runDate < this.dateFrom) return false;
      }
      if (this.dateTo && r.startedAt) {
        const runDate = new Date(r.startedAt).toISOString().slice(0, 10);
        if (runDate > this.dateTo) return false;
      }
      return true;
    });
  }
}
