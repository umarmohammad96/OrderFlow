import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { listNotifications } from '../api/notifications.js';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import { formatDateTime } from '../utils/format.js';

export default function NotificationsPage() {
  const [items, setItems] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    setError(null);
    listNotifications()
      .then((list) => setItems([...(list || [])].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))))
      .catch(setError)
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Notifications</h1>
          <p className="muted">Email &amp; SMS messages sent asynchronously when your orders are confirmed.</p>
        </div>
        <button className="btn btn-ghost" onClick={load} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>
      <ErrorBanner error={error} onRetry={load} />
      {!items && !error ? (
        <Spinner />
      ) : items && items.length === 0 ? (
        <EmptyState title="No notifications yet">Pay for an order and its confirmation email and SMS will appear here.</EmptyState>
      ) : (
        items && (
          <ul className="notification-feed">
            {items.map((n) => (
              <li key={n.id} className="card notification-item">
                <span className={`channel-icon ${n.channel === 'EMAIL' ? 'email' : 'sms'}`} aria-hidden="true">
                  {n.channel === 'EMAIL' ? '✉' : '✆'}
                </span>
                <div className="notification-body">
                  <div className="notification-head">
                    <span className={`badge ${n.channel === 'EMAIL' ? 'badge-info' : 'badge-violet'}`}>{n.channel}</span>
                    <span className="mono small">{n.recipient}</span>
                    {n.orderId && <Link to={`/orders/${n.orderId}`} className="small">Order #{n.orderId}</Link>}
                    <span className="muted small push-right">{formatDateTime(n.createdAt)}</span>
                  </div>
                  <p>{n.message}</p>
                </div>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  );
}
