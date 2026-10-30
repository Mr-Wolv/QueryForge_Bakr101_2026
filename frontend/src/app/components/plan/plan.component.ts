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
      <app-plan-explorer [artifactId]="artifactId"></app-plan-explorer>
    </section>
  `,
  styleUrls: ['./plan.component.css']
})
export class PlanComponent {
  private route = inject(ActivatedRoute);
  artifactId = '';

  constructor() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    // Callers may pass either the plan artifact ID itself (as listed in a run's
    // artifacts) or the older run stem, which kept the "-explain" suffix implicit.
    this.artifactId = id.endsWith('-explain') ? id : id + '-explain';
  }
}
