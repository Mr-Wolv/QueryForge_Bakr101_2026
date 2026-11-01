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
      <div class="filter" *ngIf="!loadError">
        <p class="template-error" *ngIf="templateLoadError" role="status">{{templateLoadError}}</p>
        <label>
          Filter by template
          <select [(ngModel)]="templateFilter" (ngModelChange)="applyFilter()">
            <option value="">All templates</option>
            <option *ngFor="let t of templateIds" [value]="t">{{t}}</option>
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
      <div class="list" *ngIf="!loading && !loadError && filteredRuns.length > 0; else empty">
        <div class="list-head">
          <button class="sort" type="button" (click)="sortByColumn('id')">Run{{sortIndicator('id')}}</button>
          <button class="sort" type="button" (click)="sortByColumn('templateId')">Template{{sortIndicator('templateId')}}</button>
          <button class="sort" type="button" (click)="sortByColumn('state')">State{{sortIndicator('state')}}</button>
          <button class="sort" type="button" (click)="sortByColumn('startedAt')">Started{{sortIndicator('startedAt')}}</button>
          <button class="sort num" type="button" (click)="sortByColumn('durationMs')">Duration{{sortIndicator('durationMs')}}</button>
        </div>
        <a class="row" *ngFor="let r of filteredRuns" [routerLink]="['/runs', r.id]">
          <span class="id">{{r.id}}</span>
          <span class="template">{{r.templateId}}</span>
          <span class="state" [class]="stateClass(r.state)">{{r.state}}</span>
          <span class="started">{{r.startedAt | date:'short'}}</span>
          <span class="num">{{r.durationMs | number}} ms</span>
        </a>
      </div>

      <div class="pagination" *ngIf="!loading && !loadError && totalPages > 1" role="navigation" aria-label="Run history pagination">
        <button type="button" (click)="goToPage(currentPage - 1)" [disabled]="currentPage === 0">Prev</button>
        <span class="page-info">Page {{currentPage + 1}} of {{totalPages}}</span>
        <button type="button" (click)="goToPage(currentPage + 1)" [disabled]="currentPage >= totalPages - 1">Next</button>
      </div>

      <ng-template #empty>
        <app-state-card *ngIf="!loadError" mode="empty" message="No runs recorded yet.">
          <p class="empty-action">
            <a routerLink="/experiments">Create your first experiment</a>
          </p>
        </app-state-card>
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
  templateIds: string[] = [];
  totalPages = 0;
  currentPage = 0;
  pageSize = 20;
  templateFilter = '';
  stateFilter = '';
  dateFrom = '';
  dateTo = '';
  sortBy = 'startedAt';
  sortDir: 'asc' | 'desc' = 'desc';
  loading = true;
  loadError: string | null = null;
  templateLoadError: string | null = null;

  readonly states = ['QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'];

  constructor() {
    // The catalog links here with the template preselected.
    this.templateFilter = this.route.snapshot.queryParamMap.get('template') ?? '';
    this.api.getExperiments().subscribe({
      next: templates => {
        this.templateIds = templates.map(template => template.id).sort();
        this.cdr.markForCheck();
      },
      error: () => {
        this.templateLoadError = 'Template filters are unavailable.';
        this.cdr.markForCheck();
      }
    });
    this.reload();
  }

  reload() {
    this.loading = true;
    this.loadError = null;
    this.api.getRuns(this.currentPage, this.pageSize, {
      state: this.stateFilter,
      templateId: this.templateFilter,
      from: this.dateFrom,
      to: this.dateTo,
      sortBy: this.sortBy,
      sortDirection: this.sortDir
    }).subscribe({
      next: page => {
        this.runs = page.content;
        this.filteredRuns = page.content;
        this.totalPages = page.totalPages;
        this.loading = false;
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

  goToPage(page: number) {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.reload();
  }

  applyFilter() {
    this.currentPage = 0;
    this.reload();
  }

  sortByColumn(column: string) {
    if (this.sortBy === column) {
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortBy = column;
      this.sortDir = 'asc';
    }
    this.currentPage = 0;
    this.reload();
  }

  sortIndicator(column: string): string {
    if (this.sortBy !== column) return '';
    return this.sortDir === 'asc' ? ' ▲' : ' ▼';
  }
}
