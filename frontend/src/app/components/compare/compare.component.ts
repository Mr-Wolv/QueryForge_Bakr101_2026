import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, ExperimentRun, ComparisonResult } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';

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
          Candidate run
          <select [(ngModel)]="b" name="b" required>
            <option value="">Select run</option>
            <option *ngFor="let r of runs" [value]="r.id">{{r.id}}</option>
          </select>
        </label>
        <button type="submit" [disabled]="loading">Compare</button>
      </form>

      <app-state-card *ngIf="error" mode="unavailable" [message]="error" />

      <div class="result" *ngIf="result">
        <h2>{{result.baseline}} <span class="vs">vs</span> {{result.candidate}}</h2>
        <div class="verdict">
          <span class="badge" [class.pass]="result.verdict.compatible" [class.fail]="!result.verdict.compatible">
            {{result.verdict.compatible ? 'COMPATIBLE' : 'INCOMPATIBLE'}}
          </span>
          <span *ngIf="result.verdict.warnings?.length" class="warnings">
            {{result.verdict.warnings.length}} warning(s)
          </span>
        </div>
        <div class="warnings-list" *ngIf="result.verdict.warnings?.length">
          <div class="card" *ngFor="let w of result.verdict.warnings">
            <p>{{w}}</p>
          </div>
        </div>
        <div class="diff" *ngIf="result.verdict.diff && (result.verdict.diff | keyvalue)?.length">
          <h3>Differences</h3>
          <div class="card" *ngFor="let entry of result.verdict.diff | keyvalue">
            <p><strong>{{entry.key}}:</strong> {{entry.value}}</p>
          </div>
        </div>
      </div>

      <div class="raw" *ngIf="!result && !error">
        <app-state-card mode="empty" message="Select two runs to compare." />
      </div>
    </section>
  `,
  styleUrls: ['./compare.component.css']
})
export class CompareComponent {
  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);
  runs: ExperimentRun[] = [];
  result?: ComparisonResult;
  loading = false;
  error?: string;
  a = '';
  b = '';

  constructor() {
    this.api.getRuns().subscribe(r => { this.runs = r; this.cdr.markForCheck(); });
  }

  compare() {
    const leftRunId = this.a;
    const rightRunId = this.b;
    if (!leftRunId || !rightRunId) return;
    this.loading = true;
    this.error = undefined;
    this.result = undefined;
    this.api.compare(leftRunId, rightRunId).subscribe({
      next: (res) => {
        this.result = {
          ...res,
          baseline: res.leftRunId,
          candidate: res.rightRunId,
        };
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err: any) => {
        this.error = err?.error?.error || 'Comparison failed';
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }
}
