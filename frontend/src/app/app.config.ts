import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),

    // Zoneless: change detection follows signal writes instead of zone.js
    // monkey-patching every async API in the browser. Smaller bundle, and no
    // stray re-render on every unrelated timer tick.
    provideZonelessChangeDetection(),

    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),

    // Order matters: auth runs first on the way out so the token is attached,
    // and error runs outermost so it sees failures from everything inside it.
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
  ],
};
