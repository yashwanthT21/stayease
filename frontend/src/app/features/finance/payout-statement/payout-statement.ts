import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { OwnerDataService } from '../../../core/services/owner-data.service';
import { ToastService } from '../../../core/services/toast.service';
import { OwnerPayoutResponse, OwnerStatementResponse } from '../../../core/models/dtos';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { StatTileComponent } from '../../../shared/ui/stat-tile';

/**
 * The owner's financial view: monthly statements and the payouts against them.
 *
 * The owner SIGNS OFF here. Finance issues a statement, and until the owner
 * approves it no payout can be created against it (enforced server-side, not just
 * hidden in Finance's UI). Rejecting sends it back with a reason, Finance corrects
 * and re-issues, and it returns here awaiting another look — so the money never
 * moves on figures the owner disputes.
 *
 * The list endpoints are RBAC-restricted to Finance/Admin/Owner, so an owner
 * request may still come back 403 in some deployments. We treat that as an
 * expected "managed elsewhere" state and show an explanatory banner rather than an
 * error page.
 */
@Component({
  selector: 'app-owner-payout-statement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, CurrencyPipe, OwnerPageHeaderComponent, OwnerDialogComponent, StatTileComponent],
  templateUrl: './payout-statement.html',
})
export class PayoutStatementComponent {
  private data = inject(OwnerDataService);
  private toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly restricted = signal(false);
  protected readonly statements = signal<OwnerStatementResponse[]>([]);
  protected readonly payouts = signal<OwnerPayoutResponse[]>([]);

  // ---- approve / reject ----
  /** The statement whose decision dialog is open, and which way. */
  protected readonly decisionTarget = signal<OwnerStatementResponse | null>(null);
  protected readonly decisionKind = signal<'approve' | 'reject'>('approve');
  protected readonly decisionNote = signal('');
  protected readonly deciding = signal(false);
  protected readonly triedDecide = signal(false);

  /** A rejection must say why — that's the whole value of it to Finance. */
  protected readonly reasonMissing = computed(
    () => this.decisionKind() === 'reject' && this.decisionNote().trim().length === 0,
  );

  /** How many statements are sitting on the owner's desk. */
  protected readonly awaitingCount = computed(
    () => this.statements().filter((s) => s.status === 'ISSUED').length,
  );

  protected readonly totalNet = computed(() =>
    this.statements().reduce((sum, s) => sum + Number(s.netPayout ?? 0), 0),
  );
  protected readonly totalPaid = computed(() =>
    this.payouts().filter((p) => p.status === 'PAID').reduce((sum, p) => sum + Number(p.amount ?? 0), 0),
  );
  protected readonly totalPending = computed(() =>
    this.payouts().filter((p) => p.status === 'PENDING').reduce((sum, p) => sum + Number(p.amount ?? 0), 0),
  );

  constructor() {
    let done = 0;
    const finish = () => {
      if (++done === 2) {
        this.loading.set(false);
      }
    };

    this.data.myStatements().subscribe({
      next: (rows) => {
        this.statements.set([...rows].sort((a, b) => b.period.localeCompare(a.period)));
        finish();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.restricted.set(true);
        }
        finish();
      },
    });

    this.data.myPayouts().subscribe({
      next: (rows) => {
        this.payouts.set(rows);
        finish();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.restricted.set(true);
        }
        finish();
      },
    });
  }

  // ---------------- approve / reject ----------------

  /** Only an ISSUED statement is the owner's to answer (mirrors the backend). */
  protected awaitsDecision(statement: OwnerStatementResponse): boolean {
    return statement.status === 'ISSUED';
  }

  protected openDecision(statement: OwnerStatementResponse, kind: 'approve' | 'reject'): void {
    this.decisionTarget.set(statement);
    this.decisionKind.set(kind);
    this.decisionNote.set('');
    this.triedDecide.set(false);
  }

  protected closeDecision(): void {
    this.decisionTarget.set(null);
  }

  protected onDecisionNote(event: Event): void {
    this.decisionNote.set((event.target as HTMLTextAreaElement).value);
  }

  protected submitDecision(): void {
    const statement = this.decisionTarget();
    if (!statement) {
      return;
    }
    this.triedDecide.set(true);
    if (this.reasonMissing()) {
      return;
    }

    const approving = this.decisionKind() === 'approve';
    const note = this.decisionNote().trim();
    const req$ = approving
      ? this.data.approveStatement(statement.id, note || undefined)
      : this.data.rejectStatement(statement.id, note);

    this.deciding.set(true);
    req$.subscribe({
      next: (saved) => {
        this.deciding.set(false);
        this.decisionTarget.set(null);
        // Replace the one row from the server's answer rather than reloading, so
        // the reader stays where they were in the table.
        this.statements.update((rows) => rows.map((r) => (r.id === saved.id ? saved : r)));
        this.toast.success(
          approving
            ? `Statement ${saved.period} approved — Finance can now release your payout.`
            : `Statement ${saved.period} rejected. Finance has been notified and will re-issue it.`,
        );
      },
      error: () => this.deciding.set(false),
    });
  }

  protected statementBadge(status: string): string {
    switch (status) {
      case 'PAID':
      case 'APPROVED':
        return 'text-bg-success';
      case 'REJECTED':
        return 'text-bg-danger';
      case 'ISSUED':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  protected payoutBadge(status: string): string {
    switch (status) {
      case 'PAID':
        return 'text-bg-success';
      case 'FAILED':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }
}
