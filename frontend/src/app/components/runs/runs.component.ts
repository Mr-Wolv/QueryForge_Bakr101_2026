import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, ExperimentRun } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';

@Component({
  selector: 'app-runs',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StateCardComponent],
  template: `
    <section class="runs">
      <h1>Run history</h1>
      <div class="filter" *ngIf="runs.length > 0">
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
      </div>
      <div class="list" *ngIf="filteredRuns.length > 0; else empty">
        <a class="row" *ngFor="let r of filteredRuns" [routerLink]="['/runs', r.id]">
          <span class="id">{{r.id}}</span>
          <span class="template">{{r.templateId}}</span>
          <span class="state" [class.succeeded]="r.state==='SUCCEEDED'" [class.failed]="r.state==='FAILED'">{{r.state}}</span>
          <span class="p95">{{r.metrics.iterationPercentiles['p95'] | number:'1.0-0'}} ms</span>
          <span class="rps">{{r.metrics.rps | number:'1.2-2'}} RPS</span>
        </a>
      </div>
      <ng-template #empty>
        <app-state-card mode="empty" message="No runs recorded yet." />
      </ng-template>
    </section>
  `,
  styleUrls: ['./runs.component.css']
})
export class RunsComponent {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  runs: ExperimentRun[] = [];
  filteredRuns: ExperimentRun[] = [];
  templateFilter = '';
  stateFilter = '';

  get templates(): string[] {
    const set = new Set(this.runs.map(r => r.templateId));
    return Array.from(set);
  }

  get states(): string[] {
    const set = new Set(this.runs.map(r => r.state));
    return Array.from(set);
  }

  constructor() {
    this.api.getRuns().subscribe(r => {
      this.runs = r;
      this.applyFilter();
      this.cdr.markForCheck();
    });
  }

  applyFilter() {
    this.filteredRuns = this.runs.filter(r => {
      if (this.templateFilter && r.templateId !== this.templateFilter) return false;
      if (this.stateFilter && r.state !== this.stateFilter) return false;
      return true;
    });
  }
}
