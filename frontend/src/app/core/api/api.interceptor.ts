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
      if (error.status === 401 && req.url.startsWith('/api/')) {
        window.location.assign('/oauth2/authorization/queryforge');
      }
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
