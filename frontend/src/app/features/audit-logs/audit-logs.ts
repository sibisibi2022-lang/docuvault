import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AuditApi } from '../../core/api/audit.api';
import type { AuditAction, AuditEntry, Page } from '../../core/models/api.models';
import { formatDateTime } from '../../shared/format';

const ACTIONS: AuditAction[] = [
  'USER_REGISTERED',
  'USER_LOGGED_IN',
  'USER_ROLE_CHANGED',
  'USER_DISABLED',
  'DOCUMENT_UPLOADED',
  'DOCUMENT_UPDATED',
  'DOCUMENT_DELETED',
  'VERSION_CREATED',
  'VERSION_RESTORED',
  'CATEGORY_CREATED',
  'CATEGORY_UPDATED',
  'CATEGORY_DELETED',
];

@Component({
  selector: 'app-audit-logs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './audit-logs.html',
})
export class AuditLogsComponent {
  private readonly api = inject(AuditApi);

  protected readonly actions = ACTIONS;
  protected readonly page = signal<Page<AuditEntry> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filter = signal<AuditAction | null>(null);
  protected readonly currentPage = signal(0);

  protected readonly formatDateTime = formatDateTime;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.list(this.filter(), this.currentPage()).subscribe({
      next: (page) => {
        this.page.set(page);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      },
    });
  }

  protected applyFilter(value: string): void {
    this.filter.set((value || null) as AuditAction | null);
    this.currentPage.set(0);
    this.load();
  }

  protected goToPage(index: number): void {
    this.currentPage.set(index);
    this.load();
  }

  /** USER_ROLE_CHANGED -> "User role changed" */
  protected label(action: AuditAction): string {
    const words = action.toLowerCase().replace(/_/g, ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  protected tone(action: AuditAction): string {
    if (action.endsWith('_DELETED') || action === 'USER_DISABLED') {
      return 'bg-red-50 text-red-700';
    }
    if (action.startsWith('USER_')) {
      return 'bg-violet-50 text-violet-700';
    }
    if (action.startsWith('VERSION_')) {
      return 'bg-amber-50 text-amber-700';
    }
    return 'bg-emerald-50 text-emerald-700';
  }
}
