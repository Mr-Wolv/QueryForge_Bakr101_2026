import { Injectable } from '@angular/core';
import {
  HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProblemDetail } from './error.model';

@Injectable()
export class ApiInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const authReq = req.clone({
      setHeaders: {
        'X-Request-Id': crypto.randomUUID()
      }
    });

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
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
}
