import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class CacheService {
  private readonly cache = new Map<string, { expiry: number; value: any }>();
  private readonly ttlMs = 30_000;
  private readonly inflight = new Map<string, Observable<any>>();

  cached<T>(key: string, request: () => Observable<T>): Observable<T> {
    const hit = this.cache.get(key);
    if (hit && hit.expiry > Date.now()) {
      return of(hit.value);
    }
    return request().pipe(
      tap((v) => this.cache.set(key, { expiry: Date.now() + this.ttlMs, value: v })),
      shareReplay(1)
    );
  }

  invalidateRuns(): void {
    for (const key of Array.from(this.cache.keys())) {
      if (key.startsWith('runs:')) this.cache.delete(key);
    }
    for (const key of Array.from(this.inflight.keys())) {
      if (key.startsWith('run:')) this.inflight.delete(key);
    }
  }

  dedup<T>(key: string, request: () => Observable<T>): Observable<T> {
    const existing = this.inflight.get(key);
    if (existing) {
      return existing as Observable<T>;
    }
    const obs = request().pipe(
      tap({
        finalize: () => this.inflight.delete(key),
      }),
      shareReplay(1)
    );
    this.inflight.set(key, obs);
    return obs;
  }

  getTtlMs(): number {
    return this.ttlMs;
  }
}
