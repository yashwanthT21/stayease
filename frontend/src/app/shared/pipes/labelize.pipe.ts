import { Pipe, PipeTransform } from '@angular/core';

/** Turns backend enum names like ISSUE_REPORTED into "Issue Reported". */
export function labelize(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return String(value)
    .toLowerCase()
    .split('_')
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(' ');
}

/** Template-side wrapper around {@link labelize}. */
@Pipe({ name: 'labelize' })
export class LabelizePipe implements PipeTransform {
  transform(value: unknown): string {
    return labelize(value);
  }
}
