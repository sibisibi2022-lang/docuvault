import { Injectable, signal } from '@angular/core';

export interface ConfirmRequest {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

interface PendingConfirm extends ConfirmRequest {
  resolve: (confirmed: boolean) => void;
}

/**
 * A real confirmation dialog, replacing window.confirm().
 *
 * <p>The native dialog cannot be styled, blocks the whole browser tab, is
 * suppressed outright in some embedded contexts, and cannot show the kind of
 * detail these actions need — such as how many documents are about to go.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly pending = signal<PendingConfirm | null>(null);

  readonly current = this.pending.asReadonly();

  ask(request: ConfirmRequest): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      this.pending.set({ ...request, resolve });
    });
  }

  respond(confirmed: boolean): void {
    const request = this.pending();
    this.pending.set(null);
    request?.resolve(confirmed);
  }
}
