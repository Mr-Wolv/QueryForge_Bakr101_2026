import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface Toast {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
  duration?: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private toasts = new BehaviorSubject<Toast[]>([]);
  readonly toasts$ = this.toasts.asObservable();

  private nextId = 0;

  show(message: string, type: Toast['type'] = 'info', duration = 3000) {
    const id = ++this.nextId;
    const toast: Toast = { id, message, type, duration };
    this.toasts.next([...this.toasts.value, toast]);
    if (duration > 0) {
      setTimeout(() => this.dismiss(id), duration);
    }
    return id;
  }

  dismiss(id: number) {
    this.toasts.next(this.toasts.value.filter(t => t.id !== id));
  }

  clear() {
    this.toasts.next([]);
  }
}
