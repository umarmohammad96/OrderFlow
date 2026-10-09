import { humanize } from '../utils/format.js';

export default function StatusBadge({ status }) {
  return <span className={`badge status-${String(status || '').toLowerCase()}`}>{humanize(status)}</span>;
}

export function StockBadge({ product }) {
  if (!product) return null;
  if (product.active === false) return <span className="badge badge-muted">Unavailable</span>;
  const qty = product.availableQuantity ?? 0;
  if (!product.inStock || qty <= 0) return <span className="badge badge-danger">Out of stock</span>;
  if (qty <= 5) return <span className="badge badge-warn">Only {qty} left</span>;
  return <span className="badge badge-success">In stock</span>;
}
