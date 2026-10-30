import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';
import { SkeletonCardComponent } from '../skeleton-card/skeleton-card.component';

@Component({
  selector: 'app-experiments',
  standalone: true,
  imports: [CommonModule, RouterLink, StateCardComponent, SkeletonCardComponent],
  template: `
    <section class="catalog">
      <h1>Experiment catalog</h1>
      <app-skeleton-card mode="loading" *ngIf="loading" />
      <div class="list" *ngIf="!loading && templates.length > 0; else empty">
        <div class="card" *ngFor="let t of templates">
          <a [routerLink]="['/runs', t.id]">
            <h2>{{t.id}} — {{t.title}}</h2>
            <p>{{t.purpose}}</p>
          </a>
          <div class="meta">
            <span>Duration: {{t.estimatedDuration}}</span>
            <span [class.destructive]="t.destructive">{{t.destructive ? 'Destructive' : 'Read-only'}}</span>
          </div>
          <div class="validation-errors" *ngIf="validationErrorsFor(t.id).length > 0">
            <p class="error" *ngFor="let err of validationErrorsFor(t.id)">{{err}}</p>
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
  loading = true;
  validationErrors: Record<string, string> = {};

  constructor() {
    this.api.getExperiments().subscribe(t => {
      this.templates = t || [];
      this.loading = false;
      this.cdr.markForCheck();
    });
  }

  launching = false;
  launchError: string | null = null;

  validateTemplate(template: any): boolean {
    const errors: Record<string, string> = {};
    for (const p of template.parameters || []) {
      const value = this.getParameterValue(template, p.name);
      if (value === null || value === undefined || value === '') {
        errors[p.name] = `${p.name} is required`;
        continue;
      }
      if (p.type === 'integer') {
        const num = Number(value);
        if (!Number.isInteger(num)) {
          errors[p.name] = `${p.name} must be an integer`;
        } else if (p.min !== undefined && num < p.min) {
          errors[p.name] = `${p.name} must be >= ${p.min}`;
        } else if (p.max !== undefined && num > p.max) {
          errors[p.name] = `${p.name} must be <= ${p.max}`;
        }
      }
      if (p.type === 'enum' && p.allowed && p.allowed.length && !p.allowed.includes(value)) {
        errors[p.name] = `${p.name} must be one of: ${p.allowed.join(', ')}`;
      }
    }
    this.validationErrors = errors;
    return Object.keys(errors).length === 0;
  }

  validationErrorsFor(templateId: string): string[] {
    const template = this.templates.find(t => t.id === templateId);
    if (!template || Object.keys(this.validationErrors).length === 0) return [];
    return template.parameters?.map((p: any) => this.validationErrors[p.name]).filter(Boolean) || [];
  }

  private getParameterValue(template: any, name: string): string | null {
    const field = template.parameters?.find((p: any) => p.name === name);
    if (!field) return null;
    if (field.min !== undefined) return String(field.min);
    if (field.allowed && field.allowed.length) return field.allowed[0];
    return null;
  }

  launch(templateId: string) {
    const template = this.templates.find(t => t.id === templateId);
    if (!template) return;
    if (!this.validateTemplate(template)) return;

    this.launching = true;
    this.launchError = null;
    const parameters: Record<string, string> = {};
    for (const p of template.parameters || []) {
      const value = this.getParameterValue(template, p.name);
      if (value !== null) parameters[p.name] = value;
    }
    this.api.createRun(templateId, parameters).subscribe({
      next: run => this.router.navigate(['/runs', run.id]),
      error: (err: any) => { this.launchError = err?.error?.message || 'Failed to launch run'; this.launching = false; },
      complete: () => { this.launching = false; }
    });
  }
}
