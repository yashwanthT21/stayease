/**
 * One place for the app's currency symbol.
 *
 * Most screens format money with Angular's `| currency` pipe, which picks up
 * `DEFAULT_CURRENCY_CODE: 'INR'` from app.config.ts. A few screens format money
 * by hand (they need "—" for empty values, which the pipe can't express); those
 * use the constant/helper below so the symbol is never hard-coded twice.
 */
export const RUPEE = '₹';

/** "1234.5" → "₹1234.50"; null/blank/NaN → the given fallback. */
export function formatRupees(value: unknown, fallback = '—'): string {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  const n = Number(value);
  return Number.isFinite(n) ? RUPEE + n.toFixed(2) : fallback;
}
