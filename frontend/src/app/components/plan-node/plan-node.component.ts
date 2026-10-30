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
    <div class="node" *ngIf="node">
      <div class="head" (click)="toggle()" [attr.tabindex]="0" (keydown.enter)="toggle()" role="treeitem" [attr.aria-expanded]="expanded">
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
      </div>
      <div class="raw-tooltip" *ngIf="isUnknown && node.raw">{{ node.raw }}</div>
      <div class="children" *ngIf="expanded && node.children?.length" role="group">
        <app-plan-node *ngFor="let child of node.children" [node]="child" [level]="level + 1" [unknownTypes]="unknownTypes"></app-plan-node>
      </div>
    </div>
  `,
  styles: [`
    .node { margin-left: 16px; }
    .head { display: flex; align-items: center; gap: 6px; padding: 4px 0; cursor: pointer; user-select: none; }
    .head:hover { background: rgba(0,0,0,0.02); }
    .caret { width: 12px; font-size: 10px; color: #666; transition: transform 0.15s; }
    .caret.expanded { transform: rotate(90deg); }
    .caret:empty::before { content: ' '; }
    .type { font-family: ui-monospace, monospace; font-size: 13px; font-weight: 600; color: #111; }
    .type.unknown { color: #b45309; background: #fef3c7; padding: 1px 6px; border-radius: 3px; }
    .relation, .alias, .key { color: #555; font-size: 12px; }
    .metrics { margin-left: auto; display: flex; gap: 8px; font-size: 11px; color: #666; font-variant-numeric: tabular-nums; }
    .unknown-badge { font-size: 10px; font-weight: 700; color: #92400e; background: #fef3c7; border: 1px solid #fcd34d; padding: 1px 6px; border-radius: 3px; letter-spacing: 0.05em; }
    .raw-tooltip { padding: 6px 10px; background: #fff7ed; border: 1px solid #fed7aa; border-radius: 4px; font-size: 12px; color: #7c2d12; margin: 2px 0 2px 24px; white-space: pre-wrap; }
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

  get props(): Record<string, string> {
    return this.node?.properties || {};
  }

  toggle() { this.expanded = !this.expanded; }
}
