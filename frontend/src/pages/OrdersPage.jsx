import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { listOrders } from '../api/orders.js';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import Pagination from '../components/Pagination.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import { formatCurrency, formatDateTime } from '../utils/format.js';

export default function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [tick, setTick] = useState(0);
  const navigate = useNavigate();

  useEffect(() => {
    setError(null);
    listOrders({ page, size: 10 }).then(setResult).catch(setError);
  }, [page, tick]);

  if (error && !result) return <ErrorBanner error={error} onRetry={() => setTick((t) => t + 1)} />;
  if (!result) return <Spinner label="Loading orders…" />;

  if (!result.content.length && page === 0) {
    return (
      <EmptyState title="No orders yet" action={<Link className="btn btn-primary" to="/">Start shopping</Link>}>
        Orders you place will show up here.
      </EmptyState>
    );
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Orders</h1>
          <p className="muted">{result.totalElements} order{result.totalElements === 1 ? '' : 's'}</p>
        </div>
      </div>
      <div className="card table-card">
        <table className="table clickable">
          <thead>
            <tr>
              <th>Order</th>
              <th>Placed</th>
              <th>Status</th>
              <th className="num">Items</th>
              <th className="num">Total</th>
            </tr>
          </thead>
          <tbody>
            {result.content.map((o) => (
              <tr key={o.id} onClick={() => navigate(`/orders/${o.id}`)}>
                <td data-label="Order"><Link to={`/orders/${o.id}`} className="strong" onClick={(e) => e.stopPropagation()}>#{o.id}</Link></td>
                <td data-label="Placed">{formatDateTime(o.createdAt)}</td>
                <td data-label="Status"><StatusBadge status={o.status} /></td>
                <td data-label="Items" className="num">{o.itemCount}</td>
                <td data-label="Total" className="num strong">{formatCurrency(o.totalAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => setParams(p ? { page: String(p) } : {})} />
    </div>
  );
}
