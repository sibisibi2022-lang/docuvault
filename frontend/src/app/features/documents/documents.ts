import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CategoriesApi } from '../../core/api/categories.api';
import { DocumentsApi } from '../../core/api/documents.api';
import { AuthStore } from '../../core/auth/auth.store';
import type { Category, DocumentSummary, Page } from '../../core/models/api.models';
import { describeFileType, formatBytes, formatDateTime, saveBlob } from '../../shared/format';

@Component({
  selector: 'app-documents',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './documents.html',
})
export class DocumentsComponent {
  private readonly api = inject(DocumentsApi);
  private readonly categoriesApi = inject(CategoriesApi);
  private readonly fb = inject(FormBuilder);
  protected readonly auth = inject(AuthStore);

  protected readonly page = signal<Page<DocumentSummary> | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<DocumentSummary | null>(null);
  protected readonly saving = signal(false);
  protected readonly selectedFile = signal<File | null>(null);

  protected readonly search = signal('');
  protected readonly categoryFilter = signal<number | null>(null);
  protected readonly currentPage = signal(0);

  protected readonly formatBytes = formatBytes;
  protected readonly formatDateTime = formatDateTime;
  protected readonly describeFileType = describeFileType;

  protected readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.maxLength(2000)]],
    categoryId: [null as number | null],
  });

  constructor() {
    this.categoriesApi.list().subscribe({
      next: (page) => this.categories.set(page.content),
      error: () => this.categories.set([]),
    });
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);

    this.api
      .list({
        query: this.search() || undefined,
        categoryId: this.categoryFilter(),
        page: this.currentPage(),
      })
      .subscribe({
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

  protected applySearch(value: string): void {
    this.search.set(value);
    this.currentPage.set(0);
    this.load();
  }

  protected applyCategory(value: string): void {
    this.categoryFilter.set(value ? Number(value) : null);
    this.currentPage.set(0);
    this.load();
  }

  protected goToPage(index: number): void {
    this.currentPage.set(index);
    this.load();
  }

  // ------------------------------------------------------------------ dialog

  protected openUpload(): void {
    this.editing.set(null);
    this.selectedFile.set(null);
    this.form.reset({ title: '', description: '', categoryId: null });
    this.error.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(doc: DocumentSummary): void {
    this.editing.set(doc);
    this.selectedFile.set(null);
    this.form.reset({
      title: doc.title,
      description: doc.description ?? '',
      categoryId: doc.categoryId,
    });
    this.error.set(null);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  protected save(): void {
    const target = this.editing();

    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!target && !this.selectedFile()) {
      this.error.set('Choose a file to upload');
      return;
    }

    this.saving.set(true);
    this.error.set(null);

    const { title, description, categoryId } = this.form.getRawValue();
    const metadata = {
      title: title.trim(),
      description: description.trim() || null,
      categoryId: categoryId ? Number(categoryId) : null,
    };

    const request = target
      ? this.api.update(target.id, metadata, this.selectedFile())
      : this.api.upload(metadata, this.selectedFile()!);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogOpen.set(false);
        this.notice.set(target ? 'Document updated' : 'Document uploaded');
        this.load();
      },
      error: (err: Error) => {
        // Includes the "already in version history" conflict, which is useful
        // information rather than a failure — so it is shown in the dialog
        // instead of closing it.
        this.error.set(err.message);
        this.saving.set(false);
      },
    });
  }

  // ----------------------------------------------------------------- actions

  protected download(doc: DocumentSummary): void {
    this.api.download(doc.id).subscribe({
      next: (blob) => saveBlob(blob, doc.fileName),
      error: (err: Error) => this.error.set(err.message),
    });
  }

  protected remove(doc: DocumentSummary): void {
    if (!confirm(`Delete "${doc.title}" and its entire version history? This cannot be undone.`)) {
      return;
    }

    this.api.delete(doc.id).subscribe({
      next: () => {
        this.notice.set('Document deleted');
        this.load();
      },
      error: (err: Error) => this.error.set(err.message),
    });
  }

  /** Employees may act on their own rows; admins and managers on any row. */
  protected canModify(doc: DocumentSummary): boolean {
    return this.auth.canManageWorkspace() || doc.ownerId === this.auth.user()?.userId;
  }
}
