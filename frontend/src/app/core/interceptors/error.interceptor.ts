import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import type { ProblemDetail } from '../models/api.models';

/** The message shown when the server sends nothing usable. */
const FALLBACK = 'Something went wrong. Please try again.';

/**
 * Turns every failed request into a plain, readable message, and signs the user
 * out when their token stops being accepted.
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401 anywhere but the login call means the token expired or was revoked.
      // Clearing the session here is what stops the app from sitting on a dead
      // token and failing every subsequent request in a confusing way.
      if (error.status === 401 && !request.url.includes('/auth/')) {
        auth.signOut();
        void router.navigate(['/login'], { queryParams: { expired: 1 } });
      }

      return throwError(() => new Error(readMessage(error)));
    }),
  );
};

function readMessage(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Could not reach the server. Check your connection and try again.';
  }

  const problem = error.error as ProblemDetail | null;

  // Field errors are the most specific thing available, so prefer them.
  if (problem?.errors) {
    const first = Object.values(problem.errors)[0];
    if (first) return first;
  }

  return problem?.detail?.trim() || FALLBACK;
}
