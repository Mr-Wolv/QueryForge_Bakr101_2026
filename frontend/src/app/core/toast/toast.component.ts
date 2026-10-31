import { Component, input, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService, Toast } from './toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toasts" aria-live="polite" aria-atomic="true">
      <div *ngFor="let t of toasts()" class="toast" [class.error]="t.type === 'error'" [class.success]="t.type === 'success'">
        <span class="message">{{ t.message }}</span>
        <button class="close" type="button" (click)="dismiss(t.id)">×</button>
      </div>
    </div>
  `,
  styles: [`
    .toasts { position: fixed; top: 1rem; right: 1rem; z-index: 9999; display: flex; flex-direction: column; gap: 0.5rem; pointer-events: none; }
    .toast { pointer-events: auto; display: flex; align-items: center; gap: 0.5rem; padding: 0.75rem 1rem; border-radius: var(--radius-md); border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text-primary); font-size: 13px; min-width: 240px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); animation: slideIn 0.2s ease-out; }
    .toast.error { border-color: var(--color-danger); background: var(--color-danger-subtle); color: var(--color-danger); }
    .toast.success { border-color: var(--color-success); background: var(--color-success-subtle); color: var(--color-success); }
    .toast .message { flex: 1; }
    .toast .close { background: none; border: none; color: inherit; font-size: 18px; line-height: 1; cursor: pointer; padding: 0 0.25rem; }
    .toast .close:hover { opacity: 0.7; }
    @keyframes slideIn { from { transform: translateX(100%); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
  `]
})
export class ToastComponent {
  private toastService = inject(ToastService);
  toasts = this.toastService.toasts$;

  dismiss(id: string) {
    this.toastService.dismiss(id);
  }
}
