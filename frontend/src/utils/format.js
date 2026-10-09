const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** Formats a number (or numeric string, as BigDecimal values may arrive) as USD. */
export function formatCurrency(value) {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) return '—';
  return currencyFormatter.format(n);
}

export function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatTime(value) {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/** "PAYMENT_PENDING" -> "Payment pending" */
export function humanize(code) {
  if (!code) return '';
  const s = String(code).toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}
