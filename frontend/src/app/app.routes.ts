import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./components/overview/overview.component').then(m => m.OverviewComponent), pathMatch: 'full' },
  { path: 'experiments', loadComponent: () => import('./components/experiments/experiments.component').then(m => m.ExperimentsComponent) },
  { path: 'runs', loadComponent: () => import('./components/runs/runs.component').then(m => m.RunsComponent) },
  { path: 'runs/:id', loadComponent: () => import('./components/run-detail/run-detail.component').then(m => m.RunDetailComponent) },
  { path: 'plan/:runId/:artifactId', loadComponent: () => import('./components/plan/plan.component').then(m => m.PlanComponent) },
  { path: 'plan/:id', loadComponent: () => import('./components/plan/plan.component').then(m => m.PlanComponent) },
  { path: 'compare', loadComponent: () => import('./components/compare/compare.component').then(m => m.CompareComponent) },
  { path: '**', redirectTo: '', pathMatch: 'full' }
];
