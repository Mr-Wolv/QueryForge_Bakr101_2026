import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { PlanExplorerComponent } from '../plan-explorer/plan-explorer.component';

@Component({
  selector: 'app-plan',
  standalone: true,
  imports: [CommonModule, RouterLink, PlanExplorerComponent],
  template: `
    <section class="plan" *ngIf="artifactId">
      <a class="back" routerLink="/runs">&larr; Run history</a>
      <h1>Explain plan</h1>
      <app-plan-explorer [artifactId]="artifactId" [runId]="runId"></app-plan-explorer>
    </section>
  `,
  styleUrls: ['./plan.component.css']
})
export class PlanComponent {
  private route = inject(ActivatedRoute);
  artifactId = '';
  runId = '';

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
  }
}
