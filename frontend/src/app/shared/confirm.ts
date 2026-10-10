import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmService } from './confirm.service';

@Component({
  selector: 'app-confirm',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirm.current(); as request) {
      <div class="fixed inset-0 z-40 grid place-items-center bg-slate-900/40 px-4"
           role="dialog" aria-modal="true" (click)="confirm.respond(false)">
        <div class="w-full max-w-sm rounded-xl bg-white p-6 shadow-xl" (click)="$event.stopPropagation()">
          <h2 class="text-base font-semibold text-slate-900">{{ request.title }}</h2>
          <p class="mt-2 text-sm text-slate-600">{{ request.message }}</p>
          <div class="mt-5 flex justify-end gap-2">
            <button type="button" class="btn-secondary" (click)="confirm.respond(false)">Cancel</button>
            <button type="button" [class]="request.danger ? 'btn-danger' : 'btn-primary'"
                    (click)="confirm.respond(true)">
              {{ request.confirmLabel ?? 'Confirm' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmComponent {
  protected readonly confirm = inject(ConfirmService);
}
