import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from './auth.service';
import { ApiService, AuthSession } from '../../services/api.service';

const OPERATOR: AuthSession = {
  authEnabled: true,
  authenticated: true,
  operator: true,
  displayName: 'operator',
};
const VIEWER: AuthSession = {
  authEnabled: true,
  authenticated: true,
  operator: false,
  displayName: 'viewer',
};
const BYPASS: AuthSession = {
  authEnabled: false,
  authenticated: false,
  operator: false,
  displayName: '',
};

function setup(session: AuthSession) {
  const api = {
    getAuthSession: vi.fn(() => of(session)),
    logout: vi.fn(() => of(undefined)),
    invalidateRuns: vi.fn(),
  };
  TestBed.configureTestingModule({
    providers: [{ provide: ApiService, useValue: api }],
  });
  const auth = TestBed.inject(AuthService);
  auth.load();
  return { auth, api };
}

describe('AuthService', () => {
  it('allows an operator to perform privileged actions', () => {
    const { auth } = setup(OPERATOR);
    expect(auth.session()?.operator).toBe(true);
    expect(auth.canOperate()).toBe(true);
  });

  it('does not let a read-only viewer mutate runs', () => {
    const { auth } = setup(VIEWER);
    expect(auth.canOperate()).toBe(false);
  });

  it('treats the explicit development bypass as unrestricted', () => {
    const { auth } = setup(BYPASS);
    expect(auth.canOperate()).toBe(true);
  });

  it('clears cached run history on sign-out', () => {
    const { auth, api } = setup(OPERATOR);

    // jsdom cannot navigate; record the assignment instead.
    const original = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { assign: vi.fn() },
    });
    try {
      auth.signOut();
    } finally {
      Object.defineProperty(window, 'location', { configurable: true, value: original });
    }

    expect(api.logout).toHaveBeenCalled();
    expect(api.invalidateRuns).toHaveBeenCalled();
    expect(auth.session()?.authenticated).toBe(false);
  });
});
