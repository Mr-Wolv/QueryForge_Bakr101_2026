import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProblemDetail } from './error.model';

export function apiInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
  const authReq = req.clone({
    setHeaders: {
      'X-Request-Id': crypto.randomUUID()
    }
  });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Never start an OIDC redirect from an arbitrary 401. The landing page
      // loads before the session is known, and the Keycloak SSO session can
      // survive a local sign-out: redirecting here turned an anonymous visit
      // and an explicit sign-out into a silent re-login. The visible "Sign in"
      // button is the only entry point, and callers render the error instead.
      const problem = error.error as ProblemDetail | undefined;
      return throwError(() => problem ?? {
        type: 'https://problem.org/internal',
        title: error.message ?? 'Unknown Error',
        status: error.status ?? 500,
        detail: error.message ?? '',
        instance: req.url
      });
    })
  );
}
