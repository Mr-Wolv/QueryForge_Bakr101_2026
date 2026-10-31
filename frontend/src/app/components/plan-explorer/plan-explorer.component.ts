import { ChangeDetectorRef, Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PlanNodeComponent } from '../plan-node/plan-node.component';
import { ApiService } from '../../services/api.service';

interface PlanNode {
  type: string;
  properties: Record<string, string>;
  children: PlanNode[];
  raw: string;
}

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
    .meta { margin-left: auto; font-size: 12px; color: var(--color-text-muted); }
    .error { margin: 0; padding: var(--space-4); color: var(--color-danger, #b42318); }
    .tree { padding: var(--space-4); overflow-x: auto; }
    .raw { margin: 0; padding: var(--space-4); background: var(--color-surface); color: var(--color-text-primary); font-size: 13px; line-height: 1.6; overflow-x: auto; max-height: 600px; overflow-y: auto; white-space: pre; }
  `]
})
export class PlanExplorerComponent implements OnChanges {
  @Input() runId = '';
  @Input() artifactId = '';

  loaded = false;
  showRaw = false;
  raw = '';
  root: PlanNode = { type: 'root', properties: {}, children: [], raw: '' };
  unknownTypes = new Set<string>();
  nodeCount = 0;
  hasError = false;
  errorMessage = '';

  private api = inject(ApiService);
  private cdr = inject(ChangeDetectorRef);

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['runId'] || changes['artifactId']) && this.artifactId) {
      this.loadPlan(this.artifactId);
    }
  }

  private loadPlan(artifactId: string) {
    this.loaded = false;
    this.hasError = false;
    this.errorMessage = '';
    this.api.getParsedPlan(artifactId, this.runId || undefined).subscribe({
      next: (data: PlanNode) => {
        this.root = data;
        this.raw = this.serialize(data);
        this.unknownTypes = this.collectUnknownTypes(data);
        this.nodeCount = this.countNodes(data);
        this.loaded = true;
        // The app runs without zone.js, so async callbacks must schedule their own
        // change detection or the tree never renders.
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
}
