import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentsApi } from '../../core/api/documents.api';
import { AuthStore } from '../../core/auth/auth.store';
import type { DocumentSummary, DocumentVersion } from '../../core/models/api.models';
import { formatBytes, formatDateTime, saveBlob } from '../../shared/format';

@Component({
  selector: 'app-versions',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './versions.html',
})
export class VersionsComponent {
  /** Bound from the route via withComponentInputBinding(). */
  readonly id = input.required<string>();

  private readonly api = inject(DocumentsApi);
  protected readonly auth = inject(AuthStore);

  protected readonly document = signal<DocumentSummary | null>(null);
  protected readonly versions = signal<DocumentVersion[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly formatBytes = formatBytes;
  protected readonly formatDateTime = formatDateTime;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const documentId = Number(this.id());
    this.loading.set(true);

    this.api.get(documentId).subscribe({
      next: (doc) => this.document.set(doc),
      error: (err: Error) => this.error.set(err.message),
    });

    this.api.versions(documentId).subscribe({
      next: (page) => {
        this.versions.set(page.content);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      },
    });
  }

  protected download(version: DocumentVersion): void {
    this.api.downloadVersion(version.id).subscribe({
      next: (blob) => saveBlob(blob, version.fileName),
      error: (err: Error) => this.error.set(err.message),
    });
  }

  protected restore(version: DocumentVersion): void {
    if (!confirm(`Make version ${version.versionNumber} the current file?`)) {
      return;
    }

    this.api.restoreVersion(Number(this.id()), version.id).subscribe({
      next: () => {
        this.notice.set(`Version ${version.versionNumber} is now current`);
        this.load();
      },
      error: (err: Error) => this.error.set(err.message),
    });
  }

  /** The current file is whichever version's checksum the document points at. */
  protected isCurrent(version: DocumentVersion): boolean {
    return this.document()?.checksum === version.checksum;
  }

  protected canModify(): boolean {
    const doc = this.document();
    return !!doc && (this.auth.canManageWorkspace() || doc.ownerId === this.auth.user()?.userId);
  }
}
