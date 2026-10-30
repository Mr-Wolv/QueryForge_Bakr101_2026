import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import { ApiService, ExperimentTemplate } from '../../services/api.service';
import { StateCardComponent } from '../state-card/state-card.component';
import { SkeletonCardComponent } from '../skeleton-card/skeleton-card.component';

type ParameterField = ExperimentTemplate['parameters'][number];

@Component({
  selector: 'app-experiments',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, StateCardComponent, SkeletonCardComponent],
  template: `
    <section class="catalog">
      <header class="catalog-intro">
        <p class="eyebrow">THE EXPERIMENT CATALOG</p>
        <h1>Choose a question worth measuring.</h1>
        <p class="subtitle">Tune each workload before you start. Every run becomes inspectable evidence, not just a number.</p>
      </header>

      <app-skeleton-card mode="loading" *ngIf="loading" />
      <div class="catalog-error" *ngIf="!loading && loadError" role="alert">
        <p>{{loadError}}</p>
        <button type="button" class="retry" (click)="loadTemplates()">Try again</button>
      </div>
      <div class="list" *ngIf="!loading && !loadError && templates.length > 0">
        <article class="card" *ngFor="let t of templates" [id]="anchorId(t.id)">
          <a class="experiment-title" [routerLink]="['/runs']" [queryParams]="{template: t.id}">
            <span class="experiment-id">{{t.id}}</span>
            <h2>{{t.title}}</h2>
            <p>{{t.purpose}}</p>
          </a>
          <p class="hypothesis"><span>Question</span>{{t.hypothesis}}</p>
          <div class="meta">
            <span>Estimated {{t.estimatedDuration}}</span>
            <span [class.destructive]="t.destructive">{{t.destructive ? 'Environment-changing' : 'Read-only'}}</span>
          </div>

          <div class="workload" *ngIf="t.parameters.length > 0">
            <div class="workload-heading">
              <h3>Shape the workload</h3>
              <span>{{t.parameters.length}} {{t.parameters.length === 1 ? 'setting' : 'settings'}}</span>
            </div>
            <div class="parameter-grid">
              <label class="parameter" *ngFor="let p of t.parameters" [attr.for]="fieldId(t.id, p.name)">
                <span class="parameter-label">{{p.name}}<small *ngIf="p.unit"> · {{p.unit}}</small></span>
                <select
                  *ngIf="p.allowed && p.allowed.length > 0; else parameterInput"
                  [id]="fieldId(t.id, p.name)"
                  [name]="fieldId(t.id, p.name)"
                  [attr.aria-required]="p.name !== 'OUT'"
                  [(ngModel)]="parameterValues[t.id][p.name]"
                  [ngModelOptions]="{standalone: true}"
                  [attr.aria-invalid]="validationErrors[t.id]?.[p.name] ? 'true' : null"
                  [attr.aria-describedby]="validationErrors[t.id]?.[p.name] ? fieldId(t.id, p.name) + '-error' : null">
                  <option *ngFor="let option of p.allowed" [value]="option">{{option}}</option>
                </select>
                <ng-template #parameterInput>
                  <input
                    [id]="fieldId(t.id, p.name)"
                    [name]="fieldId(t.id, p.name)"
                    type="text"
                    [attr.inputmode]="p.type === 'integer' ? 'numeric' : null"
                    [attr.aria-required]="p.name !== 'OUT'"
                    [placeholder]="p.name === 'OUT' ? 'Auto' : ''"
                    [(ngModel)]="parameterValues[t.id][p.name]"
                    [ngModelOptions]="{standalone: true}"
                    [attr.aria-invalid]="validationErrors[t.id]?.[p.name] ? 'true' : null"
                    [attr.aria-describedby]="validationErrors[t.id]?.[p.name] ? fieldId(t.id, p.name) + '-error' : null">
                </ng-template>
                <small class="field-help" *ngIf="p.name === 'OUT'">Leave blank for an auto-name.</small>
                <small class="field-help" *ngIf="p.min !== null && p.min !== undefined && p.max !== null && p.max !== undefined && p.type !== 'enum'">
                  {{p.min}}–{{p.max}} {{p.unit}}
                </small>
                <small class="field-error" *ngIf="validationErrors[t.id]?.[p.name]" [id]="fieldId(t.id, p.name) + '-error'">
                  {{validationErrors[t.id][p.name]}}
                </small>
              </label>
            </div>
            <p class="run-preview" aria-live="polite">
              <span>RUN PREVIEW</span>
              {{parameterPreview(t)}}
            </p>
          </div>

          <button class="launch" type="button" (click)="launch(t.id)" [disabled]="launching">
            {{launching ? 'Starting…' : 'Start experiment'}}
            <span aria-hidden="true">→</span>
          </button>
        </article>
      </div>
      <app-state-card *ngIf="!loading && !loadError && templates.length === 0" mode="empty" message="No experiments are registered yet." />
      <p class="error" *ngIf="launchError" role="alert">{{launchError}}</p>
    </section>
  `,
  styleUrls: ['./experiments.component.css']
})
export class ExperimentsComponent {
  private api = inject(ApiService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  templates: ExperimentTemplate[] = [];
  parameterValues: Record<string, Record<string, string>> = {};
  loading = true;
  loadError = '';
  validationErrors: Record<string, Record<string, string>> = {};
  launching = false;
  launchError = '';

  constructor() {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.loadError = '';
    this.api.getExperiments().subscribe({
      next: templates => {
        this.templates = templates ?? [];
        this.parameterValues = Object.fromEntries(this.templates.map(template => [
          template.id,
          Object.fromEntries(template.parameters.map(field => [field.name, this.defaultValue(field)]))
        ]));
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.loadError = this.errorMessage(error, 'Could not load the experiment catalog.');
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  anchorId(templateId: string): string {
    return templateId.toLowerCase();
  }

  fieldId(templateId: string, fieldName: string): string {
    return `${templateId.toLowerCase()}-${fieldName.toLowerCase()}`;
  }

  parameterPreview(template: ExperimentTemplate): string {
    return template.parameters
      .map(field => {
        const value = this.parameterValues[template.id]?.[field.name]?.trim();
        return value ? `${field.name}=${value}${field.unit ? ` ${field.unit}` : ''}` : null;
      })
      .filter((value): value is string => value !== null)
      .join('  ·  ') || 'Choose workload settings to preview this run.';
  }

  validateTemplate(template: ExperimentTemplate): boolean {
    const errors: Record<string, string> = {};
    for (const field of template.parameters) {
      const value = this.parameterValues[template.id]?.[field.name]?.trim() ?? '';
      if (!value) {
        if (field.name !== 'OUT') errors[field.name] = `${field.name} is required`;
        continue;
      }
      if (field.type === 'integer') {
        const number = Number(value);
        if (!Number.isInteger(number)) {
          errors[field.name] = `${field.name} must be an integer`;
        } else if (field.min !== null && field.min !== undefined && number < Number(field.min)) {
          errors[field.name] = `${field.name} must be at least ${field.min}`;
        } else if (field.max !== null && field.max !== undefined && number > Number(field.max)) {
          errors[field.name] = `${field.name} must be at most ${field.max}`;
        }
      } else if (field.type === 'duration') {
        if (!/^\d+(?:s|m)?$/.test(value)) {
          errors[field.name] = `${field.name} must be seconds (for example 60) or minutes (for example 2m)`;
        } else {
          const seconds = value.endsWith('m') ? Number(value.slice(0, -1)) * 60 : Number(value.replace(/s$/, ''));
          if (field.min !== null && field.min !== undefined && seconds < Number(field.min)) {
            errors[field.name] = `${field.name} must be at least ${field.min}s`;
          } else if (field.max !== null && field.max !== undefined && seconds > Number(field.max)) {
            errors[field.name] = `${field.name} must be at most ${field.max}s`;
          }
        }
      } else if (field.type === 'enum' && field.allowed?.length && !field.allowed.includes(value)) {
        errors[field.name] = `${field.name} must be one of: ${field.allowed.join(', ')}`;
      } else if (field.type === 'string' && !/^[A-Za-z0-9._-]{1,64}$/.test(value)) {
        errors[field.name] = `${field.name} may contain letters, numbers, dots, underscores, or hyphens (up to 64 characters)`;
      }
    }
    this.validationErrors[template.id] = errors;
    return Object.keys(errors).length === 0;
  }

  launch(templateId: string): void {
    const template = this.templates.find(candidate => candidate.id === templateId);
    if (!template || !this.validateTemplate(template)) return;

    this.launching = true;
    this.launchError = '';
    const parameters = Object.fromEntries(
      Object.entries(this.parameterValues[templateId] ?? {})
        .filter(([, value]) => value.trim().length > 0)
    );
    this.api.createRun(templateId, parameters)
      .pipe(switchMap(run => this.api.startRun(run.id)))
      .subscribe({
        next: run => this.router.navigate(['/runs', run.id]),
        error: error => {
          this.launchError = this.errorMessage(error, 'The run could not be started. Check the environment and try again.');
          this.launching = false;
          this.cdr.markForCheck();
        },
        complete: () => {
          this.launching = false;
          this.cdr.markForCheck();
        }
      });
  }

  private defaultValue(field: ParameterField): string {
    if (field.name === 'OUT') return '';
    if (field.name === 'RATE') return '10';
    if (field.name === 'DUR') return '30';
    if (field.name === 'VUS') return '10';
    if (field.allowed?.length) return field.allowed[0];
    return field.min !== null && field.min !== undefined ? String(field.min) : '';
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null) {
      if ('status' in error && typeof error.status === 'number' && [0, 502, 503, 504].includes(error.status)) {
        return `API unavailable (HTTP ${error.status}). Start the backend and try again.`;
      }
      if ('detail' in error && typeof error.detail === 'string') {
        return error.detail;
      }
    }
    return fallback;
  }
}
