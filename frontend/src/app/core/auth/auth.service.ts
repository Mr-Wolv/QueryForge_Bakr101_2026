import { computed, Injectable, inject, signal } from '@angular/core';
import { ApiService, AuthSession } from '../../services/api.service';
import { catchError, EMPTY } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  readonly session = signal<AuthSession | null>(null);
  readonly error = signal<string | null>(null);
  readonly canOperate = computed(() => {
    const session = this.session();
    return session !== null && (!session.authEnabled || session.operator);
  });

  load(): void {
    this.api.getAuthSession().pipe(
      catchError(() => {
        this.error.set('Authentication status could not be loaded.');
        return EMPTY;
      }),
    ).subscribe(session => {
      this.error.set(null);
      this.session.set(session);
    });
  }

  signIn(): void {
    window.location.assign('/oauth2/authorization/queryforge');
  }

  signOut(): void {
    this.api.logout().subscribe({
      next: () => {
        this.session.set({
          authEnabled: true,
          authenticated: false,
          operator: false,
          displayName: '',
        });
        this.error.set(null);
        window.location.assign('/');
      },
      error: () => {
        this.error.set('Sign-out failed. Please retry.');
      },
    });
  }
}
