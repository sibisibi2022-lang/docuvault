import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CategoriesApi } from '../../core/api/categories.api';
import type { Category } from '../../core/models/api.models';
import { formatDateTime } from '../../shared/format';

@Component({
  selector: 'app-categories',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './categories.html',
})
export class CategoriesComponent {
  private readonly api = inject(CategoriesApi);
  private readonly fb = inject(FormBuilder);

  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Category | null>(null);
  protected readonly saving = signal(false);

  protected readonly formatDateTime = formatDateTime;

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', [Validators.maxLength(500)]],
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (page) => {
        this.categories.set(page.content);
        this.loading.set(false);
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.form.reset({ name: '', description: '' });
    this.error.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(category: Category): void {
    this.editing.set(category);
    this.form.reset({ name: category.name, description: category.description ?? '' });
    this.error.set(null);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);

    const { name, description } = this.form.getRawValue();
    const target = this.editing();
    const request = target
      ? this.api.update(target.id, name.trim(), description.trim() || null)
      : this.api.create(name.trim(), description.trim() || null);

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogOpen.set(false);
        this.notice.set(target ? 'Category updated' : 'Category created');
        this.load();
      },
      error: (err: Error) => {
        this.error.set(err.message);
        this.saving.set(false);
      },
    });
  }

  protected remove(category: Category): void {
    if (!confirm(`Delete the category "${category.name}"?`)) {
      return;
    }

    this.api.delete(category.id).subscribe({
      next: () => {
        this.notice.set('Category deleted');
        this.load();
      },
      // The API refuses to delete a category that still has documents, and
      // says so — surface that rather than a generic failure.
      error: (err: Error) => this.error.set(err.message),
    });
  }
}
