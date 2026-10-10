import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentsApi } from '../../core/api/documents.api';
import { AuthStore } from '../../core/auth/auth.store';
import type { DashboardStats, DocumentSummary } from '../../core/models/api.models';
import { describeFileType, formatBytes, formatDateTime } from '../../shared/format';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.html',
})
export class DashboardComponent {
  private readonly api = inject(DocumentsApi);
  protected readonly auth = inject(AuthStore);

  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly recent = signal<DocumentSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly formatBytes = formatBytes;
  protected readonly formatDateTime = formatDateTime;
  protected readonly describeFileType = describeFileType;

  /** Widest bar in the file-type breakdown, used to scale the others. */
  protected readonly busiestType = computed(() =>
    Math.max(1, ...(this.stats()?.byFileType ?? []).map((row) => row.count)),
  );

  constructor() {
    this.load();
  }

  private load(): void {
    // Aggregates are computed in SQL by the API rather than by pulling every
    // document into the browser and counting there.
    this.api.stats().subscribe({
      next: (stats) => this.stats.set(stats),
      error: (err: Error) => this.error.set(err.message),
    });

    this.api.list({ size: 5 }).subscribe({
      next: (page) => {
        this.recent.set(page.content);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      },
    });
  }
}
