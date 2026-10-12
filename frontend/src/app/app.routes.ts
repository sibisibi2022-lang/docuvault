import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard',
  },

  // Login
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · DocuVault',
    loadComponent: () =>
      import('./features/auth/login/login').then(
        (m) => m.LoginComponent
      ),
  },

  // Register
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · DocuVault',
    loadComponent: () =>
      import('./features/auth/register/register').then(
        (m) => m.RegisterComponent
      ),
  },

  // Forgot Password
  {
    path: 'forgot-password',
    canActivate: [guestGuard],
    title: 'Forgot password · DocuVault',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then(
        (m) => m.ForgotPassword
      ),
  },

  // Reset Password
  {
    path: 'reset-password',
    canActivate: [guestGuard],
    title: 'Reset password · DocuVault',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password').then(
        (m) => m.ResetPassword
      ),
  },

  // Authenticated application
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shared/shell').then(
        (m) => m.ShellComponent
      ),
    children: [
      {
        path: 'dashboard',
        title: 'Dashboard · DocuVault',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then(
            (m) => m.DashboardComponent
          ),
      },
      {
        path: 'documents',
        title: 'Documents · DocuVault',
        loadComponent: () =>
          import('./features/documents/documents').then(
            (m) => m.DocumentsComponent
          ),
      },
      {
        path: 'documents/:id/versions',
        title: 'Version history · DocuVault',
        loadComponent: () =>
          import('./features/versions/versions').then(
            (m) => m.VersionsComponent
          ),
      },
      {
        path: 'categories',
        canActivate: [roleGuard('ADMIN', 'MANAGER')],
        title: 'Categories · DocuVault',
        loadComponent: () =>
          import('./features/categories/categories').then(
            (m) => m.CategoriesComponent
          ),
      },
      {
        path: 'audit-logs',
        canActivate: [roleGuard('ADMIN', 'MANAGER')],
        title: 'Audit log · DocuVault',
        loadComponent: () =>
          import('./features/audit-logs/audit-logs').then(
            (m) => m.AuditLogsComponent
          ),
      },
      {
        path: 'profile',
        title: 'Profile · DocuVault',
        loadComponent: () =>
          import('./features/profile/profile').then(
            (m) => m.ProfileComponent
          ),
      },
    ],
  },

  // Unknown routes
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
