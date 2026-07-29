import { Pipe, PipeTransform } from '@angular/core';

/** Turns backend enum names like ISSUE_REPORTED into "Issue Reported". */
@Pipe({ name: 'labelize' })
export class LabelizePipe implements PipeTransform {
  transform(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '';
    }
    return String(value)
      .toLowerCase()
      .split('_')
      .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
      .join(' ');
  }
}
