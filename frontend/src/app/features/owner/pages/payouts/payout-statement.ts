import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { OwnerDataService } from '../../data/owner-data.service';
import { OwnerPayoutResponse, OwnerStatementResponse } from '../../../../core/models/dtos';
import { LabelizePipe } from '../../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../ui/owner-page-header';
import { StatTileComponent } from '../../ui/stat-tile';

/**
 * The owner's financial view: monthly statements and the payouts against them.
 *
 * These endpoints are RBAC-restricted to Finance/Admin on the backend, so an
 * owner request may come back 403. We treat that as an expected "managed
 * elsewhere" state and show an explanatory banner rather than an error page.
 */
@Component({
  selector: 'app-owner-payout-statement',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, CurrencyPipe, OwnerPageHeaderComponent, StatTileComponent],
  templateUrl: './payout-statement.html',
})
export class PayoutStatementComponent {
  private data = inject(OwnerDataService);

  protected readonly loading = signal(true);
  protected readonly restricted = signal(false);
  protected readonly statements = signal<OwnerStatementResponse[]>([]);
  protected readonly payouts = signal<OwnerPayoutResponse[]>([]);

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

  protected statementBadge(status: string): string {
    switch (status) {
      case 'PAID':
        return 'text-bg-success';
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
