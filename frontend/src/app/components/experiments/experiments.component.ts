import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';

@Component({
  selector: 'app-experiments',
  standalone: true,
  imports: [CommonModule, RouterLink, StateCardComponent],
  template: `
    <section class="catalog">
      <h1>Experiment catalog</h1>
      <div class="list" *ngIf="templates.length > 0; else empty">
        <div class="card" *ngFor="let t of templates">
          <a [routerLink]="['/runs', t.id]">
            <h2>{{t.id}} — {{t.title}}</h2>
            <p>{{t.purpose}}</p>
          </a>
          <div class="meta">
            <span>Duration: {{t.estimatedDuration}}</span>
            <span [class.destructive]="t.destructive">{{t.destructive ? 'Destructive' : 'Read-only'}}</span>
          </div>
          <button class="launch" (click)="launch(t.id)" [disabled]="launching">
            {{launching ? 'Launching…' : 'Launch run'}}
          </button>
        </div>
      </div>
      <ng-template #empty>
        <app-state-card mode="empty" message="No experiments available." />
      </ng-template>
      <p class="error" *ngIf="launchError">{{launchError}}</p>
    </section>
  `,
  styleUrls: ['./experiments.component.css']
})
export class ExperimentsComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  templates: any[] = [];

  constructor() {
    this.api.getExperiments().subscribe(t => {
      this.templates = t || [];
      this.cdr.markForCheck();
    });
  }

  launching = false;
  launchError: string | null = null;

  launch(templateId: string) {
    this.launching = true;
    this.launchError = null;
    const parameters: Record<string, string> = {};
    for (const p of this.templates.find(t => t.id === templateId)?.parameters || []) {
      if (p.min !== undefined) parameters[p.name] = String(p.min);
      else if (p.allowed && p.allowed.length) parameters[p.name] = p.allowed[0];
    }
    this.api.createRun(templateId, parameters).subscribe({
      next: run => this.router.navigate(['/runs', run.id]),
      error: (err: any) => { this.launchError = err?.error?.message || 'Failed to launch run'; this.launching = false; },
      complete: () => { this.launching = false; }
    });
  }
}
