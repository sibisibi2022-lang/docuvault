import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../auth/auth.store';

/**
 * Attaches the bearer token to API calls.
 *
 * <p>One interceptor replaces the ten places the original hand-built an
 * `HttpHeaders` with `localStorage.getItem('token')` — ten chances to forget.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthStore).accessToken();

  // Only ever send the token to our own API. Without this check, any third
  // party URL the app later fetches would receive the user's credentials.
  const isOurApi = request.url.startsWith(environment.apiBaseUrl);

  if (!token || !isOurApi) {
    return next(request);
  }

  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
