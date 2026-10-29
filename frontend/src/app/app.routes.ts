import { Routes } from '@angular/router';
import { OverviewComponent } from './components/overview/overview.component';
import { ExperimentsComponent } from './components/experiments/experiments.component';
import { RunsComponent } from './components/runs/runs.component';
import { RunDetailComponent } from './components/run-detail/run-detail.component';
import { PlanComponent } from './components/plan/plan.component';
import { CompareComponent } from './components/compare/compare.component';

export const routes: Routes = [
  { path: '', component: OverviewComponent, pathMatch: 'full' },
  { path: 'experiments', component: ExperimentsComponent },
  { path: 'runs', component: RunsComponent },
  { path: 'runs/:id', component: RunDetailComponent },
  { path: 'plan/:id', component: PlanComponent },
  { path: 'compare', component: CompareComponent },
  { path: '**', redirectTo: '', pathMatch: 'full' }
];
