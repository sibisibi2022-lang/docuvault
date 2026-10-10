import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { describeFileType, formatBytes } from './format';
import { validateFile } from './upload-rules';

type PreviewKind = 'image' | 'pdf' | 'text' | 'generic' | 'none';

/**
 * Shows what a chosen file actually is, before it is uploaded.
 *
 * <p>Everything is rendered from the local File object, so nothing is sent to
 * the server until the user commits. Images and PDFs are shown through an
 * object URL; PDFs use the browser's own viewer in an iframe rather than
 * pulling in a rendering library.
 */
@Component({
  selector: 'app-file-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './file-preview.html',
})
export class FilePreviewComponent {
  readonly file = input<File | null>(null);

  private readonly sanitizer = inject(DomSanitizer);

  /** Held so it can be revoked; an un-revoked object URL pins the blob for the tab's life. */
  private objectUrl: string | null = null;

  protected readonly imageUrl = signal<string | null>(null);
  protected readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  protected readonly textSnippet = signal<string | null>(null);
  protected readonly textTruncated = signal(false);

  protected readonly kind = computed<PreviewKind>(() => {
    const file = this.file();
    if (!file) return 'none';
    if (file.type.startsWith('image/')) return 'image';
    if (file.type === 'application/pdf') return 'pdf';
    if (file.type === 'text/plain' || file.type === 'text/csv') return 'text';
    return 'generic';
  });

  protected readonly problem = computed(() => {
    const file = this.file();
    return file ? validateFile(file) : null;
  });

  protected readonly label = computed(() => {
    const file = this.file();
    return file ? describeFileType(file.type) : '';
  });

  protected readonly size = computed(() => {
    const file = this.file();
    return file ? formatBytes(file.size) : '';
  });

  constructor() {
    effect(() => {
      const file = this.file();
      this.release();

      if (!file) {
        return;
      }

      const kind = this.kind();

      if (kind === 'image' || kind === 'pdf') {
        this.objectUrl = URL.createObjectURL(file);
        if (kind === 'image') {
          this.imageUrl.set(this.objectUrl);
        } else {
          // #toolbar=0&view=FitH asks the built-in viewer for a clean fit.
          this.pdfUrl.set(
            this.sanitizer.bypassSecurityTrustResourceUrl(`${this.objectUrl}#toolbar=0&view=FitH`),
          );
        }
        return;
      }

      if (kind === 'text') {
        // Read only the head of the file: a 25 MB CSV should not be pulled into
        // memory as a string just to show the first few lines.
        const head = file.slice(0, 4096);
        head.text().then((text) => {
          this.textSnippet.set(text);
          this.textTruncated.set(file.size > head.size);
        });
      }
    });

    inject(DestroyRef).onDestroy(() => this.release());
  }

  private release(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
    this.imageUrl.set(null);
    this.pdfUrl.set(null);
    this.textSnippet.set(null);
    this.textTruncated.set(false);
  }
}
