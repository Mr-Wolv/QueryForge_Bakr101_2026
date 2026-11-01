import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { PlanExplorerComponent } from '../plan-explorer/plan-explorer.component';

@Component({
  selector: 'app-plan',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PlanExplorerComponent],
  template: `
    <section class="plan" *ngIf="artifactId">
      <a class="back" routerLink="/runs">&larr; Run history</a>
      <h1>Explain plan</h1>
      <label class="compare-select" *ngIf="runsWithPlans.length > 0">
        Compare with run
        <select [(ngModel)]="compareRunId" (ngModelChange)="selectComparisonRun($event)">
          <option value="">No comparison</option>
          <option *ngFor="let run of runsWithPlans" [value]="run.id">{{run.id}}</option>
        </select>
      </label>
      <p *ngIf="runListError" role="status">{{runListError}}</p>
      <app-plan-explorer
        [artifactId]="artifactId"
        [runId]="runId"
        [compareRunId]="compareRunId"
        [compareArtifactId]="compareArtifactId">
      </app-plan-explorer>
    </section>
  `,
  styles: [`
    .compare-select { display: flex; flex-direction: column; gap: 6px; max-width: 32rem; margin: 0 0 1rem; }
    .compare-select select { padding: 8px; font: inherit; }
  `],
  styleUrls: ['./plan.component.css']
})
export class PlanComponent {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);
  artifactId = '';
  runId = '';
  compareRunId = '';
  compareArtifactId = '';
  runsWithPlans: Array<{ id: string; artifactId: string }> = [];
  runListError = '';

  constructor() {
    // Prefer the run-scoped route (/plan/:runId/:artifactId): artifact IDs are
    // already the canonical name (`explain-plan`), and the run scope lets the
    // backend resolve both manifest-backed and legacy catalog plans.
    const artifact = this.route.snapshot.paramMap.get('artifactId');
    const runId = this.route.snapshot.paramMap.get('runId');
    if (artifact) {
      this.artifactId = artifact;
      this.runId = runId || '';
    } else {
      this.artifactId = this.route.snapshot.paramMap.get('id') || '';
    }
    this.api.getRuns(0, 100).subscribe({
      next: page => {
        this.runsWithPlans = page.content
          .filter(run => run.id !== this.runId)
          .map(run => ({
            id: run.id,
            artifactId: run.artifacts.find(item =>
              item.kind === 'explain-plan' || item.artifactId.startsWith('explain'))?.artifactId ?? '',
          }))
          .filter(run => run.artifactId.length > 0);
      },
      error: () => {
        this.runListError = 'Other plans are unavailable, but this run can still be inspected.';
      }
    });
  }

  selectComparisonRun(runId: string) {
    this.compareArtifactId = this.runsWithPlans.find(run => run.id === runId)?.artifactId ?? '';
  }
}
