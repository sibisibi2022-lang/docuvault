import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';

/**
 * Every route is lazy and every private route is guarded.
 *
 * <p>The role guards here decide what is worth rendering. They are not the
 * security boundary — the API applies the same rules to the signed token.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · DocuVault',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · DocuVault',
    loadComponent: () =>
      import('./features/auth/register/register').then((m) => m.RegisterComponent),
  },

  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/shell').then((m) => m.ShellComponent),
    children: [
      {
        path: 'dashboard',
        title: 'Dashboard · DocuVault',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
      },
      {
        path: 'documents',
        title: 'Documents · DocuVault',
        loadComponent: () =>
          import('./features/documents/documents').then((m) => m.DocumentsComponent),
      },
      {
        path: 'documents/:id/versions',
        title: 'Version history · DocuVault',
        loadComponent: () => import('./features/versions/versions').then((m) => m.VersionsComponent),
      },
      {
        path: 'categories',
        canActivate: [roleGuard('ADMIN', 'MANAGER')],
        title: 'Categories · DocuVault',
        loadComponent: () =>
          import('./features/categories/categories').then((m) => m.CategoriesComponent),
      },
      {
        path: 'audit-logs',
        canActivate: [roleGuard('ADMIN', 'MANAGER')],
        title: 'Audit log · DocuVault',
        loadComponent: () =>
          import('./features/audit-logs/audit-logs').then((m) => m.AuditLogsComponent),
      },
      {
        path: 'profile',
        title: 'Profile · DocuVault',
        loadComponent: () => import('./features/profile/profile').then((m) => m.ProfileComponent),
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];
