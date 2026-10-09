import { describe, expect, it } from 'vitest';
import { formatCurrency, humanize } from './format.js';

describe('formatCurrency', () => {
  it('formats numbers as USD with two decimals', () => {
    expect(formatCurrency(1234.5)).toBe('$1,234.50');
    expect(formatCurrency(0)).toBe('$0.00');
  });

  it('accepts numeric strings (BigDecimal serialised as string)', () => {
    expect(formatCurrency('19.99')).toBe('$19.99');
  });

  it('rounds to cents', () => {
    expect(formatCurrency(2.005 + 0.0001)).toBe('$2.01');
  });

  it('returns a dash for missing or invalid values', () => {
    expect(formatCurrency(null)).toBe('—');
    expect(formatCurrency(undefined)).toBe('—');
    expect(formatCurrency('abc')).toBe('—');
  });
});

describe('humanize', () => {
  it('turns enum codes into labels', () => {
    expect(humanize('PAYMENT_PENDING')).toBe('Payment pending');
    expect(humanize('SHIPPED')).toBe('Shipped');
  });
});
