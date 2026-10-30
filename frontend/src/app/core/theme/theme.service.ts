import { Injectable, inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly storageKey = 'queryforge-theme';

  get current(): 'light' | 'dark' {
    const stored = localStorage.getItem(this.storageKey);
    if (stored === 'dark' || stored === 'light') return stored;
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
    return 'light';
  }

  apply(theme: 'light' | 'dark'): void {
    this.document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(this.storageKey, theme);
  }

  toggle(): 'light' | 'dark' {
    const next = this.current === 'light' ? 'dark' : 'light';
    this.apply(next);
    return next;
  }
}
