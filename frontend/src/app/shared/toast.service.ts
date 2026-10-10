import { Injectable, signal } from '@angular/core';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

/**
 * Transient feedback, replacing the inline banners that pushed the page around
 * and stayed visible until the next action happened to clear them.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly items = signal<Toast[]>([]);
  private nextId = 1;

  readonly toasts = this.items.asReadonly();

  success(message: string): void {
    this.push('success', message, 4000);
  }

  info(message: string): void {
    this.push('info', message, 4000);
  }

  /** Errors linger: they usually need reading, and sometimes acting on. */
  error(message: string): void {
    this.push('error', message, 8000);
  }

  dismiss(id: number): void {
    this.items.update((list) => list.filter((toast) => toast.id !== id));
  }

  private push(tone: ToastTone, message: string, ttlMs: number): void {
    const id = this.nextId++;
    this.items.update((list) => [...list, { id, tone, message }]);
    setTimeout(() => this.dismiss(id), ttlMs);
  }
}
