import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { firstValueFrom, of, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { apiInterceptor } from './api.interceptor';

describe('apiInterceptor', () => {
  it('tags every request with a correlation id', async () => {
    const request = new HttpRequest('GET', '/api/runs');
    let seen: HttpRequest<unknown> | null = null;

    await firstValueFrom(apiInterceptor(request, req => {
      seen = req;
      return of(new HttpResponse({ status: 200 }));
    }));

    expect(seen).not.toBeNull();
    expect(seen!.headers.get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('surfaces a 401 problem without starting an OIDC redirect', async () => {
    const problem = {
      type: 'about:blank',
      title: 'Unauthorized',
      status: 401,
      detail: 'Authentication required',
    };
    const request = new HttpRequest('GET', '/api/runs');
    const before = window.location.href;

    const observed = await firstValueFrom(
      apiInterceptor(request, () => throwError(() => new HttpErrorResponse({
        status: 401,
        error: problem,
        url: '/api/runs',
      }))).pipe(catchError(error => of(error))),
    );

    // The caller decides how to render the problem; the interceptor must not
    // navigate away. A redirect here re-authenticates through Keycloak SSO and
    // silently defeats an explicit sign-out.
    expect(observed).toEqual(problem);
    expect(window.location.href).toBe(before);
  });
});
