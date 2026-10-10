import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CategoriesApi } from '../../core/api/categories.api';
import { DocumentsApi } from '../../core/api/documents.api';
import { AuthStore } from '../../core/auth/auth.store';
import type { BulkResult, Category, DocumentSummary, Page } from '../../core/models/api.models';
import { ConfirmService } from '../../shared/confirm.service';
import { FilePreviewComponent } from '../../shared/file-preview';
import { describeFileType, formatBytes, formatDateTime, saveBlob } from '../../shared/format';
import { ToastService } from '../../shared/toast.service';
import { validateFile } from '../../shared/upload-rules';

@Component({
  selector: 'app-documents',
  imports: [ReactiveFormsModule, RouterLink, FilePreviewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './documents.html',
})
export class DocumentsComponent {
  private readonly api = inject(DocumentsApi);
  private readonly categoriesApi = inject(CategoriesApi);
  private readonly fb = inject(FormBuilder);
  private readonly toasts = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthStore);

  protected readonly page = signal<Page<DocumentSummary> | null>(null);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<DocumentSummary | null>(null);
  protected readonly saving = signal(false);
  protected readonly dialogError = signal<string | null>(null);
  protected readonly selectedFile = signal<File | null>(null);
  protected readonly dragging = signal(false);

  protected readonly search = signal('');
  protected readonly categoryFilter = signal<number | null>(null);
  protected readonly currentPage = signal(0);

  /** Ids ticked in the table. Held as a Set so toggling stays O(1). */
  protected readonly selectedIds = signal<ReadonlySet<number>>(new Set());
  protected readonly bulkBusy = signal(false);

  protected readonly formatBytes = formatBytes;
  protected readonly formatDateTime = formatDateTime;
  protected readonly describeFileType = describeFileType;

  protected readonly rows = computed(() => this.page()?.content ?? []);

  /** Only rows the caller may act on can be bulk-selected. */
  protected readonly selectableRows = computed(() => this.rows().filter((doc) => this.canModify(doc)));

  protected readonly selectedCount = computed(() => this.selectedIds().size);

  protected readonly allSelected = computed(() => {
    const selectable = this.selectableRows();
    return selectable.length > 0 && selectable.every((doc) => this.selectedIds().has(doc.id));
  });

  /** Blocks submit for a file the server would reject anyway. */
  protected readonly fileProblem = computed(() => {
    const file = this.selectedFile();
    return file ? validateFile(file) : null;
  });

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
    this.loadError.set(null);

    this.api
      .list({
        query: this.search() || undefined,
        categoryId: this.categoryFilter(),
        page: this.currentPage(),
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          // Drop selections for rows that are no longer on screen, so a hidden
          // tick cannot silently take part in the next bulk action.
          const visible = new Set(page.content.map((doc) => doc.id));
          this.selectedIds.update((ids) => new Set([...ids].filter((id) => visible.has(id))));
          this.loading.set(false);
        },
        error: (err: Error) => {
          this.loadError.set(err.message);
          this.loading.set(false);
        },
      });
  }

  // ---------------------------------------------------------------- filtering

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

  // ---------------------------------------------------------------- selection

  protected toggleRow(id: number): void {
    this.selectedIds.update((ids) => {
      const next = new Set(ids);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  protected toggleAll(): void {
    this.selectedIds.set(
      this.allSelected() ? new Set() : new Set(this.selectableRows().map((doc) => doc.id)),
    );
  }

  protected clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  // -------------------------------------------------------------- bulk actions

  protected async bulkDelete(): Promise<void> {
    const ids = [...this.selectedIds()];
    const confirmed = await this.confirm.ask({
      title: `Delete ${ids.length} document${ids.length === 1 ? '' : 's'}?`,
      message:
        'Every version of each document is removed, along with its stored files. This cannot be undone.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;

    this.bulkBusy.set(true);
    this.api.bulkDelete(ids).subscribe({
      next: (result) => this.finishBulk(result, 'deleted'),
      error: (err: Error) => this.failBulk(err),
    });
  }

  protected bulkMove(value: string): void {
    const ids = [...this.selectedIds()];
    if (!ids.length) return;

    const categoryId = value ? Number(value) : null;
    this.bulkBusy.set(true);

    this.api.bulkMoveToCategory(ids, categoryId).subscribe({
      next: (result) => this.finishBulk(result, 'moved'),
      error: (err: Error) => this.failBulk(err),
    });
  }

  protected bulkDownload(): void {
    const ids = [...this.selectedIds()];
    this.bulkBusy.set(true);

    this.api.bulkDownload(ids).subscribe({
      next: (blob) => {
        saveBlob(blob, 'docuvault-documents.zip');
        this.bulkBusy.set(false);
        this.toasts.success(`Downloaded ${ids.length} document${ids.length === 1 ? '' : 's'} as a zip`);
      },
      error: (err: Error) => this.failBulk(err),
    });
  }

  /** Reports partial success honestly rather than claiming the whole batch worked. */
  private finishBulk(result: BulkResult, verb: string): void {
    this.bulkBusy.set(false);
    this.clearSelection();
    this.load();

    if (result.failures.length === 0) {
      this.toasts.success(`${result.succeeded} document${result.succeeded === 1 ? '' : 's'} ${verb}`);
      return;
    }

    this.toasts.error(
      `${result.succeeded} of ${result.requested} ${verb}. ` +
        `${result.failures.length} skipped: ${result.failures[0].reason}`,
    );
  }

  private failBulk(error: Error): void {
    this.bulkBusy.set(false);
    this.toasts.error(error.message);
  }

  // ------------------------------------------------------------------- dialog

  protected openUpload(): void {
    this.editing.set(null);
    this.selectedFile.set(null);
    this.form.reset({ title: '', description: '', categoryId: null });
    this.dialogError.set(null);
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
    this.dialogError.set(null);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
    this.selectedFile.set(null);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.acceptFile(input.files?.[0] ?? null);
  }

  // Drag and drop. preventDefault on dragover is what stops the browser from
  // navigating away to open the dropped file.
  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDragLeave(): void {
    this.dragging.set(false);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.acceptFile(event.dataTransfer?.files?.[0] ?? null);
  }

  private acceptFile(file: File | null): void {
    this.selectedFile.set(file);
    this.dialogError.set(null);

    // Offer the filename as the title when the user has not typed one, which is
    // what they would almost always type anyway.
    if (file && !this.form.controls.title.value.trim()) {
      this.form.patchValue({ title: file.name.replace(/\.[^.]+$/, '') });
    }
  }

  protected save(): void {
    const target = this.editing();

    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!target && !this.selectedFile()) {
      this.dialogError.set('Choose a file to upload');
      return;
    }
    if (this.fileProblem()) {
      return;
    }

    this.saving.set(true);
    this.dialogError.set(null);

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
        this.selectedFile.set(null);
        this.toasts.success(target ? 'Document updated' : 'Document uploaded');
        this.load();
      },
      error: (err: Error) => {
        // Includes the duplicate-version conflict, which is information rather
        // than failure — so it stays in the dialog instead of closing it.
        this.dialogError.set(err.message);
        this.saving.set(false);
      },
    });
  }

  // ------------------------------------------------------------- row actions

  protected download(doc: DocumentSummary): void {
    this.api.download(doc.id).subscribe({
      next: (blob) => saveBlob(blob, doc.fileName),
      error: (err: Error) => this.toasts.error(err.message),
    });
  }

  protected async remove(doc: DocumentSummary): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'Delete this document?',
      message: `"${doc.title}" and all ${doc.versionCount} of its versions will be removed. This cannot be undone.`,
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!confirmed) return;

    this.api.delete(doc.id).subscribe({
      next: () => {
        this.toasts.success('Document deleted');
        this.load();
      },
      error: (err: Error) => this.toasts.error(err.message),
    });
  }

  protected canModify(doc: DocumentSummary): boolean {
    return this.auth.canManageWorkspace() || doc.ownerId === this.auth.user()?.userId;
  }
}
