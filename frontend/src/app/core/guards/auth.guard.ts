import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthStore } from '../auth/auth.store';
import type { Role } from '../models/api.models';

/**
 * Blocks routes for signed-out visitors.
 *
 * <p>The original had no guards at all, so /dashboard, /documents and
 * /audit-logs all rendered for anyone who typed the URL. They showed no data —
 * the API refused it — but the shell, the navigation and the admin controls
 * were all visible.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  // Remember where they were going so sign-in can return them there.
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Keeps signed-in users off the login and registration screens. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  const router = inject(Router);

  return auth.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};

/**
 * Restricts a route to the given roles.
 *
 * <p>This hides a screen the user has no business seeing. It is not the
 * security boundary — the API enforces the same rule from the signed token, so
 * a tampered local role buys nothing but a page full of 403s.
 */
export function roleGuard(...allowed: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthStore);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }

    const role = auth.role();
    return role && allowed.includes(role) ? true : router.createUrlTree(['/dashboard']);
  };
}
