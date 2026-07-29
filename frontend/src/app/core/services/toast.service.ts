import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'danger' | 'warning' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}

/** Tiny signal-backed toast queue rendered by ToastContainerComponent. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  show(text: string, kind: ToastKind = 'info', timeoutMs = 4000): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, kind, text }]);
    if (timeoutMs > 0) {
      setTimeout(() => this.dismiss(id), timeoutMs);
    }
  }

  success(text: string): void {
    this.show(text, 'success');
  }
  error(text: string): void {
    this.show(text, 'danger', 6000);
  }
  info(text: string): void {
    this.show(text, 'info');
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
