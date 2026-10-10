import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toasts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4"
         role="status" aria-live="polite">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="pointer-events-auto flex w-full max-w-md items-start gap-3 rounded-lg border px-4 py-3 shadow-lg"
             [class]="tone(toast.tone)">
          <span class="mt-0.5 shrink-0" aria-hidden="true">{{ icon(toast.tone) }}</span>
          <p class="flex-1 text-sm">{{ toast.message }}</p>
          <button type="button" class="shrink-0 text-lg leading-none opacity-50 hover:opacity-100"
                  (click)="toasts.dismiss(toast.id)" aria-label="Dismiss">&times;</button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  protected readonly toasts = inject(ToastService);

  protected tone(tone: string): string {
    switch (tone) {
      case 'success':
        return 'border-emerald-200 bg-emerald-50 text-emerald-900';
      case 'error':
        return 'border-red-200 bg-red-50 text-red-900';
      default:
        return 'border-slate-200 bg-white text-slate-900';
    }
  }

  protected icon(tone: string): string {
    switch (tone) {
      case 'success':
        return '✓';
      case 'error':
        return '!';
      default:
        return 'i';
    }
  }
}
