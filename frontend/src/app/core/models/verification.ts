import { VerificationStatus } from './enums';

/**
 * A guest's verification is stored as a three-value tier (UNVERIFIED,
 * ID_VERIFIED, TRUSTED) but every screen that *reads* it only cares about the
 * binary question "has this guest been verified?". Keeping that mapping here
 * means the manager's Guests table and the guest's own Account Standing card can
 * never disagree about what a tier means.
 */
export const VERIFIED_STATUSES: readonly VerificationStatus[] = ['ID_VERIFIED', 'TRUSTED'];

export const VERIFICATION_LABELS = {
  UNVERIFIED: 'Unverified',
  ID_VERIFIED: 'Verified',
  TRUSTED: 'Verified',
} as const satisfies Record<VerificationStatus, string>;

/**
 * The two options a reader picks between. Unverified is FIRST because it is the
 * default: a guest starts unverified, and a dropdown whose fallback option is
 * "Verified" would imply the opposite.
 */
export const VERIFICATION_DISPLAY_OPTIONS = ['Unverified', 'Verified'] as const;

export function isVerified(status: string | null | undefined): boolean {
  return !!status && VERIFIED_STATUSES.includes(status as VerificationStatus);
}

/** "ID_VERIFIED" → "Verified"; anything unknown/absent reads as "Unverified". */
export function verificationLabel(status: string | null | undefined): string {
  return isVerified(status) ? 'Verified' : 'Unverified';
}

/**
 * The reverse of {@link verificationLabel}, for writing a reader's pick back.
 *
 * "Verified" resolves to ID_VERIFIED rather than TRUSTED: both read as verified,
 * but TRUSTED is an earned standing, not something to confer by flipping a
 * dropdown. Picking "Verified" on an already-TRUSTED guest is a no-op (their
 * current label already matches, so nothing is saved) and never demotes them.
 */
export function verificationStatusFor(label: string): VerificationStatus {
  return label === 'Verified' ? 'ID_VERIFIED' : 'UNVERIFIED';
}
