import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Centralized UI state store.
 *
 * <p>Provides application-level signals for loading and error states.
 * Components can read these directly; services can update them.
 */
@Injectable({ providedIn: 'root' })
export class UiStateService {
  private _globalLoading = signal(false);
  private _globalError = signal<string | null>(null);

  readonly globalLoading = this._globalLoading.asReadonly();
  readonly globalError = this._globalError.asReadonly();

  setLoading(loading: boolean): void {
    this._globalLoading.set(loading);
  }

  setError(message: string | null): void {
    this._globalError.set(message);
  }

  clearError(): void {
    this._globalError.set(null);
  }
}
