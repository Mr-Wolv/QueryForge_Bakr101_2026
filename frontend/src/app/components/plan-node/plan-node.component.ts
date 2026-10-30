import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

interface PlanNode {
  type: string;
  properties: Record<string, string>;
  children: PlanNode[];
  raw: string;
}

@Component({
  selector: 'app-plan-node',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="node" *ngIf="node" role="treeitem" [attr.aria-expanded]="hasChildren ? expanded : null">
      <button *ngIf="hasChildren; else leafHead" class="head" type="button" (click)="toggle()"
        [attr.aria-label]="(expanded ? 'Collapse ' : 'Expand ') + normalizedType + ' plan node'">
        <span class="caret" [class.expanded]="expanded">&#9654;</span>
        <span class="type" [class.unknown]="isUnknown">{{ normalizedType }}</span>
        <span class="relation" *ngIf="relation">{{ relation }}</span>
        <span class="alias" *ngIf="alias">{{ alias }}</span>
        <span class="key" *ngIf="key">{{ key }}</span>
        <span class="metrics" *ngIf="hasMetrics">
          <span class="metric" title="actual time">{{ props['actualStartMs'] }}..{{ props['actualEndMs'] }} ms</span>
          <span class="metric" title="rows">{{ props['rows'] }} rows</span>
          <span class="metric" title="cost">{{ props['planCostStart'] }}..{{ props['planCostEnd'] }}</span>
          <span class="metric" title="buffers" *ngIf="props['buffers']">{{ props['buffers'] }}</span>
        </span>
        <span class="unknown-badge" *ngIf="isUnknown && node.type.length">UNKNOWN</span>
      </button>
      <ng-template #leafHead>
        <div class="head">
          <span class="caret"></span>
          <span class="type" [class.unknown]="isUnknown">{{ normalizedType }}</span>
          <span class="relation" *ngIf="relation">{{ relation }}</span>
          <span class="alias" *ngIf="alias">{{ alias }}</span>
          <span class="key" *ngIf="key">{{ key }}</span>
          <span class="metrics" *ngIf="hasMetrics">
            <span class="metric" title="actual time">{{ props['actualStartMs'] }}..{{ props['actualEndMs'] }} ms</span>
            <span class="metric" title="rows">{{ props['rows'] }} rows</span>
            <span class="metric" title="cost">{{ props['planCostStart'] }}..{{ props['planCostEnd'] }}</span>
            <span class="metric" title="buffers" *ngIf="props['buffers']">{{ props['buffers'] }}</span>
          </span>
          <span class="unknown-badge" *ngIf="isUnknown && node.type.length">UNKNOWN</span>
        </div>
      </ng-template>
      <div class="raw-tooltip" *ngIf="isUnknown && node.raw">{{ node.raw }}</div>
      <div class="children" *ngIf="expanded && node.children?.length" role="group">
        <app-plan-node *ngFor="let child of node.children" [node]="child" [level]="level + 1" [unknownTypes]="unknownTypes"></app-plan-node>
      </div>
    </div>
  `,
  styles: [`
    .node { margin-left: var(--space-4); }
    .head { display: flex; width: 100%; min-width: 0; align-items: center; gap: var(--space-2); padding: 4px 0; border: 0; border-radius: var(--radius-sm); background: transparent; color: var(--color-text-primary); font: inherit; text-align: left; white-space: normal; cursor: pointer; user-select: none; }
    .head:hover { background: var(--color-accent-subtle); }
    .head:disabled { cursor: default; }
    .caret { width: 12px; flex: 0 0 12px; font-size: 10px; color: var(--color-text-muted); transition: transform var(--transition-fast); }
    .caret.expanded { transform: rotate(90deg); }
    .caret:empty::before { content: ' '; }
    .type { font-family: var(--font-mono); font-size: 13px; font-weight: 600; color: var(--color-text-primary); }
    .type.unknown { color: var(--color-warning); background: var(--color-warning-bg); padding: 1px 6px; border-radius: var(--radius-sm); }
    .relation, .alias, .key { color: var(--color-text-secondary); font-size: 12px; }
    .metrics { margin-left: auto; display: flex; flex-wrap: wrap; gap: var(--space-2); font-size: 11px; color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .unknown-badge { font-size: 10px; font-weight: 700; color: var(--color-warning); background: var(--color-warning-bg); border: 1px solid var(--color-warning); padding: 1px 6px; border-radius: var(--radius-sm); letter-spacing: 0.05em; }
    .raw-tooltip { padding: var(--space-2) var(--space-3); background: var(--color-warning-bg); border: 1px solid var(--color-warning); border-radius: var(--radius-sm); font-size: 12px; color: var(--color-text-primary); margin: 2px 0 2px var(--space-6); white-space: pre-wrap; }
  `]
})
export class PlanNodeComponent {
  @Input() node: PlanNode | null = null;
  @Input() level = 0;
  @Input() unknownTypes: Set<string> = new Set();

  expanded = true;

  get isUnknown(): boolean {
    if (!this.node) return false;
    const t = this.node.type.toLowerCase();
    return this.unknownTypes.has(this.node.type.toUpperCase());
  }

  get normalizedType(): string {
    if (!this.node) return '';
    return this.node.type;
  }

  get relation(): string {
    const p = this.node?.properties || {};
    const v = p['relation'] || p['on'] || '';
    return v.startsWith('on ') ? v.substring(3) : v;
  }

  get alias(): string {
    return this.node?.properties?.['alias'] || '';
  }

  get key(): string {
    return this.node?.properties?.['key'] || '';
  }

  get hasMetrics(): boolean {
    if (!this.node) return false;
    const p = this.node.properties;
    return !!(p['actualStartMs'] || p['rows'] || p['planCostStart'] || p['buffers']);
  }

  get hasChildren(): boolean {
    return !!this.node?.children?.length;
  }

  get props(): Record<string, string> {
    return this.node?.properties || {};
  }

  toggle() { this.expanded = !this.expanded; }
}
