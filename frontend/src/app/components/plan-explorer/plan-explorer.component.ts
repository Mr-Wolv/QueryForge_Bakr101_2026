import { ChangeDetectorRef, Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlanNodeComponent } from '../plan-node/plan-node.component';
import { ApiService } from '../../services/api.service';

interface PlanNode {
  type: string;
  properties: Record<string, string>;
  children: PlanNode[];
  raw: string;
  costScore?: number;
}

interface PlanDiffRow {
  path: string;
  baselineType: string;
  candidateType: string;
  baselineCost: string;
  candidateCost: string;
  costDelta: string;
  change: string;
}

const EXPENSIVE_TYPES = new Set(['seq scan', 'index scan', 'bitmap heap scan', 'tid scan']);

@Component({
  selector: 'app-plan-explorer',
  standalone: true,
  imports: [CommonModule, PlanNodeComponent],
  template: `
    <section class="plan-explorer" *ngIf="loaded">
      <div class="toolbar">
        <button class="btn" (click)="toggleView()">
          {{ showRaw ? 'Tree' : 'Raw' }}
        </button>
        <button class="btn" (click)="copyRaw()" [disabled]="hasError">Copy</button>
        <span class="meta">{{ nodeCount }} nodes</span>
      </div>

      <p class="error" *ngIf="hasError" role="alert">{{ errorMessage }}</p>

      <section class="diff" *ngIf="compareRunId">
        <h2>Plan diff</h2>
        <p *ngIf="comparisonLoading" role="status">Loading comparison plan…</p>
        <p class="error" *ngIf="comparisonError" role="alert">{{comparisonError}}</p>
        <ng-container *ngIf="!comparisonLoading && !comparisonError">
          <p>Nodes are aligned by their position in the plan tree; cost changes show candidate minus baseline.</p>
          <table *ngIf="diffRows.length > 0">
            <thead>
              <tr>
                <th scope="col">Path</th>
                <th scope="col">Baseline node / cost</th>
                <th scope="col">Candidate node / cost</th>
                <th scope="col">Change</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of diffRows">
                <td>{{row.path}}</td>
                <td>{{row.baselineType}} · {{row.baselineCost}}</td>
                <td>{{row.candidateType}} · {{row.candidateCost}}</td>
                <td>{{row.change}}<span *ngIf="row.costDelta"> ({{row.costDelta}})</span></td>
              </tr>
            </tbody>
          </table>
          <p *ngIf="diffRows.length === 0">No node or estimated-cost differences found.</p>
        </ng-container>
      </section>

      <div *ngIf="!showRaw && !hasError" class="tree" role="tree" aria-label="Query execution plan">
        <app-plan-node
          *ngFor="let node of root.children"
          [node]="node"
          [level]="0"
          [unknownTypes]="unknownTypes">
        </app-plan-node>
      </div>

      <pre *ngIf="showRaw && !hasError" class="raw">{{ raw }}</pre>
    </section>
  `,
  styles: [`
    .plan-explorer { border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .toolbar { display: flex; gap: var(--space-2); padding: var(--space-3); border-bottom: 1px solid var(--color-border); background: var(--color-surface-elevated); align-items: center; }
    .btn { font: inherit; font-size: 13px; padding: 6px 12px; border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text-primary); border-radius: var(--radius-sm); cursor: pointer; }
    .btn:hover:not(:disabled) { background: var(--color-accent-subtle); }
    .btn:disabled { opacity: .55; cursor: not-allowed; }
    .meta { margin-left: auto; font-size: 12px; color: var(--color-text-secondary); }
    .error { margin: 0; padding: var(--space-4); color: var(--color-danger, #b42318); }
    .tree { padding: var(--space-4); overflow-x: auto; }
    .raw { margin: 0; padding: var(--space-4); background: var(--color-surface); color: var(--color-text-primary); font-size: 13px; line-height: 1.6; overflow-x: auto; max-height: 600px; overflow-y: auto; white-space: pre; }
    .diff { margin: var(--space-4); padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow-x: auto; }
    .diff h2 { margin-top: 0; font-size: 1rem; }
    .diff table { width: 100%; border-collapse: collapse; }
    .diff th, .diff td { padding: 8px; border-bottom: 1px solid var(--color-border); text-align: left; }
  `]
})
export class PlanExplorerComponent implements OnChanges {
  @Input() runId = '';
  @Input() artifactId = '';
  @Input() compareRunId = '';
  @Input() compareArtifactId = '';

  loaded = false;
  showRaw = false;
  raw = '';
  root: PlanNode = { type: 'root', properties: {}, children: [], raw: '' };
  unknownTypes = new Set<string>();
  nodeCount = 0;
  hasError = false;
  errorMessage = '';
  comparisonLoading = false;
  comparisonError = '';
  diffRows: PlanDiffRow[] = [];

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['runId'] || changes['artifactId']) && this.artifactId) {
      this.loadPlan(this.artifactId);
    } else if (changes['compareRunId'] || changes['compareArtifactId']) {
      if (this.compareRunId && this.loaded && !this.hasError) this.loadComparisonPlan();
      else if (!this.compareRunId) this.resetComparison();
    }
  }

  private loadPlan(artifactId: string) {
    this.loaded = false;
    this.hasError = false;
    this.errorMessage = '';
    this.api.getParsedPlan(artifactId, this.runId || undefined).subscribe({
      next: (data: PlanNode) => {
        this.root = data;
        this.computeCostScores(data);
        this.raw = this.serialize(data);
        this.unknownTypes = this.collectUnknownTypes(data);
        this.nodeCount = this.countNodes(data);
        this.loaded = true;
        if (this.compareRunId) this.loadComparisonPlan();
        this.cdr.markForCheck();
      },
      error: () => {
        this.hasError = true;
        this.errorMessage = `The explain plan "${artifactId}" could not be loaded.`;
        this.root = { type: 'root', properties: {}, children: [], raw: '' };
        this.unknownTypes = new Set<string>();
        this.nodeCount = 0;
        this.loaded = true;
        this.cdr.markForCheck();
      }
    });
  }

  private loadComparisonPlan() {
    if (!this.compareRunId || !this.compareArtifactId) {
      this.resetComparison();
      if (this.compareRunId) this.comparisonError = 'The selected run has no available explain plan.';
      return;
    }
    this.comparisonLoading = true;
    this.comparisonError = '';
    this.diffRows = [];
    this.api.getParsedPlan(this.compareArtifactId, this.compareRunId).subscribe({
      next: (candidate: PlanNode) => {
        this.diffRows = this.comparePlans(this.root, candidate);
        this.comparisonLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.comparisonLoading = false;
        this.comparisonError = `The explain plan for run "${this.compareRunId}" could not be loaded.`;
        this.cdr.markForCheck();
      }
    });
  }

  private resetComparison() {
    this.comparisonLoading = false;
    this.comparisonError = '';
    this.diffRows = [];
  }

  private comparePlans(baseline: PlanNode, candidate: PlanNode): PlanDiffRow[] {
    const flatten = (root: PlanNode) => {
      const nodes = new Map<string, PlanNode>();
      const visit = (node: PlanNode, path: string) => {
        node.children.forEach((child, index) => {
          const childPath = path ? `${path}.${index + 1}` : `${index + 1}`;
          nodes.set(childPath, child);
          visit(child, childPath);
        });
      };
      visit(root, '');
      return nodes;
    };
    const baselineNodes = flatten(baseline);
    const candidateNodes = flatten(candidate);
    const paths = [...new Set([...baselineNodes.keys(), ...candidateNodes.keys()])]
      .sort((left, right) => left.localeCompare(right, undefined, { numeric: true }));

    return paths.flatMap(path => {
      const before = baselineNodes.get(path);
      const after = candidateNodes.get(path);
      const beforeCost = this.planCost(before);
      const afterCost = this.planCost(after);
      const costDelta = beforeCost !== null && afterCost !== null
        ? Number((afterCost - beforeCost).toFixed(2))
        : null;
      const typeChanged = before?.type !== after?.type;
      if (before && after && !typeChanged && (costDelta === null || costDelta === 0)) return [];
      const changes = [
        !before ? 'node added' : !after ? 'node removed' : typeChanged ? 'node type changed' : '',
        costDelta !== null && costDelta !== 0 ? 'estimated cost changed' : '',
      ].filter(Boolean);
      return [{
        path,
        baselineType: before?.type ?? '—',
        candidateType: after?.type ?? '—',
        baselineCost: beforeCost === null ? 'n/a' : beforeCost.toString(),
        candidateCost: afterCost === null ? 'n/a' : afterCost.toString(),
        costDelta: costDelta === null ? '' : `${costDelta > 0 ? '+' : ''}${costDelta}`,
        change: changes.join(', '),
      }];
    });
  }

  private planCost(node: PlanNode | undefined): number | null {
    if (!node) return null;
    const cost = Number(node.properties['planCostEnd']);
    return Number.isFinite(cost) ? cost : null;
  }

  toggleView() { this.showRaw = !this.showRaw; }
  copyRaw() { if (!this.hasError) void navigator.clipboard.writeText(this.raw); }

  private serialize(node: PlanNode): string {
    const lines: string[] = [];
    const walk = (n: PlanNode, depth: number) => {
      const pad = '  '.repeat(depth);
      lines.push(`${pad}${n.type}${n.children.length ? ' (' + n.children.length + ' children)' : ''}`);
      for (const child of n.children) walk(child, depth + 1);
    };
    walk(node, 0);
    return lines.join('\n');
  }

  private collectUnknownTypes(node: PlanNode): Set<string> {
    const set = new Set<string>();
    const walk = (n: PlanNode) => {
      const t = n.type.toLowerCase();
      if (!t.startsWith('seq') && !t.startsWith('index') && !t.startsWith('bitmap') &&
          !t.startsWith('hash') && !t.startsWith('merge') && !t.startsWith('nested') &&
          !t.startsWith('gather') && !t.startsWith('cte') && !t.startsWith('subquery') &&
          !t.startsWith('materialize') && !t.startsWith('memoize') && !t.startsWith('sort') &&
          !t.startsWith('aggregate') && !t.startsWith('limit') && !t.startsWith('result') &&
          !t.startsWith('incremental') && t !== 'root') {
        set.add(n.type.toUpperCase());
      }
      for (const child of n.children) walk(child);
    };
    walk(node);
    return set;
  }

  private countNodes(node: PlanNode): number {
    let count = 1;
    for (const child of node.children) count += this.countNodes(child);
    return count;
  }

  private computeCostScores(node: PlanNode): void {
    const costEnd = Number(node.properties['planCostEnd']);
    const actualEnd = Number(node.properties['actualEndMs']);
    const rows = Number(node.properties['rows']);
    const isExpensive = EXPENSIVE_TYPES.has(node.type.toLowerCase());

    let score = 0;
    if (!Number.isNaN(costEnd)) score += Math.log10(Math.max(costEnd, 1)) * 10;
    if (!Number.isNaN(actualEnd)) score += Math.log10(Math.max(actualEnd, 1)) * 5;
    if (!Number.isNaN(rows) && rows > 10000) score += Math.log10(Math.max(rows / 1000, 1)) * 5;
    if (isExpensive) score += 15;

    node.costScore = score;
    for (const child of node.children) this.computeCostScores(child);
  }

  costClass(node: PlanNode): string {
    const score = node.costScore ?? 0;
    if (score >= 30) return 'cost-high';
    if (score >= 15) return 'cost-medium';
    if (score > 0) return 'cost-low';
    return 'cost-none';
  }
}
