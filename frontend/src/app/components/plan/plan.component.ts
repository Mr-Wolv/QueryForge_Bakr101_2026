import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-plan',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="plan" *ngIf="plan">
      <a class="back" routerLink="/runs">&larr; Run history</a>
      <h1>Explain plan</h1>
      <pre class="raw">{{plan}}</pre>
    </section>
  `,
  styleUrls: ['./plan.component.css']
})
export class PlanComponent {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);
  plan?: string;

  constructor() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.api.getArtifactMeta(id + '-explain').subscribe(meta => {
      this.api.getArtifact(id + '-explain').subscribe(blob => {
        const reader = new FileReader();
        reader.onload = () => this.plan = reader.result as string;
        reader.readAsText(blob);
      });
    });
  }
}
