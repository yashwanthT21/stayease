import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe, SlicePipe } from '@angular/common';
import { OwnerDataService } from '../../data/owner-data.service';
import { GuestReviewResponse } from '../../../../core/models/dtos';
import { OwnerPageHeaderComponent } from '../../ui/owner-page-header';

interface CategoryStat {
  key: string;
  label: string;
  avg: number;
  count: number;
}

/**
 * Guest-satisfaction analytics rolled up across every review left on the
 * owner's properties: an overall average, per-category breakdown, a 1–5 star
 * distribution and the most recent comments.
 */
@Component({
  selector: 'app-owner-review-analytics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, SlicePipe, OwnerPageHeaderComponent],
  templateUrl: './review-analytics.html',
})
export class ReviewAnalyticsComponent {
  private data = inject(OwnerDataService);

  protected readonly loading = signal(true);
  protected readonly reviews = signal<GuestReviewResponse[]>([]);

  protected readonly total = computed(() => this.reviews().length);

  /** Overall score for one review — fall back to the category mean if absent. */
  private overallOf(r: GuestReviewResponse): number | null {
    if (r.overallScore != null) {
      return Number(r.overallScore);
    }
    const parts = [r.cleanlinessScore, r.accuracyScore, r.locationScore, r.valueScore].filter(
      (v): v is number => v != null,
    );
    if (!parts.length) {
      return null;
    }
    return parts.reduce((a, b) => a + b, 0) / parts.length;
  }

  protected readonly overallAvg = computed(() => {
    const scores = this.reviews().map((r) => this.overallOf(r)).filter((v): v is number => v != null);
    if (!scores.length) {
      return 0;
    }
    return scores.reduce((a, b) => a + b, 0) / scores.length;
  });

  protected readonly categories = computed<CategoryStat[]>(() => {
    const defs: { key: keyof GuestReviewResponse; label: string }[] = [
      { key: 'cleanlinessScore', label: 'Cleanliness' },
      { key: 'accuracyScore', label: 'Accuracy' },
      { key: 'locationScore', label: 'Location' },
      { key: 'valueScore', label: 'Value' },
    ];
    return defs.map((d) => {
      const vals = this.reviews()
        .map((r) => r[d.key])
        .filter((v): v is number => typeof v === 'number');
      const avg = vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : 0;
      return { key: String(d.key), label: d.label, avg, count: vals.length };
    });
  });

  /** Counts per whole-star bucket, 5 → 1 for display. */
  protected readonly distribution = computed(() => {
    const buckets: Record<number, number> = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    for (const r of this.reviews()) {
      const o = this.overallOf(r);
      if (o == null) {
        continue;
      }
      const star = Math.min(5, Math.max(1, Math.round(o)));
      buckets[star]++;
    }
    return [5, 4, 3, 2, 1].map((star) => ({ star, count: buckets[star] }));
  });

  protected readonly recent = computed(() =>
    this.reviews()
      .filter((r) => r.comments && r.comments.trim().length > 0)
      .slice()
      .sort((a, b) => (b.submittedDate ?? '').localeCompare(a.submittedDate ?? ''))
      .slice(0, 8),
  );

  constructor() {
    this.data.myReviews().subscribe({
      next: (rows) => {
        this.reviews.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected pct(value: number, max = 5): number {
    return Math.round((value / max) * 100);
  }

  protected barPct(count: number): number {
    const t = this.total();
    return t ? Math.round((count / t) * 100) : 0;
  }

  protected reviewOverall(r: GuestReviewResponse): number | null {
    return this.overallOf(r);
  }
}
