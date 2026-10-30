import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type SkeletonMode = 'loading' | 'error';

@Component({
  selector: 'app-skeleton-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton" [class.error]="mode() === 'error'">
      <div class="shimmer">
        <div class="line" *ngFor="let _ of lines"></div>
      </div>
      <p class="message" *ngIf="message()">{{ message() }}</p>
    </div>
  `,
  styles: [`
    .skeleton {
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-lg);
      padding: var(--space-6);
      min-height: 120px;
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
    }
    .skeleton.error {
      border-left: 3px solid var(--color-danger);
    }
    .shimmer {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      flex: 1;
    }
    .line {
      height: 12px;
      border-radius: var(--radius-sm);
      background: linear-gradient(90deg, var(--color-border-light) 25%, var(--color-border) 50%, var(--color-border-light) 75%);
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
    }
    .line:nth-child(1) { width: 60%; }
    .line:nth-child(2) { width: 80%; }
    .line:nth-child(3) { width: 45%; }
    .message {
      margin: 0;
      font-size: var(--text-sm);
      color: var(--color-text-secondary);
    }
    .error .message {
      color: var(--color-danger);
    }
    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `]
})
export class SkeletonCardComponent {
  mode = input<SkeletonMode>('loading');
  message = input<string | undefined>(undefined);
  lines = Array(3).fill(0);
}
