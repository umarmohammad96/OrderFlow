import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { listNotifications } from '../api/notifications.js';
import { cancelOrder, getOrder } from '../api/orders.js';
import { listPayments, pay } from '../api/payments.js';
import { ErrorBanner, Spinner } from '../components/Feedback.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import StatusTimeline from '../components/StatusTimeline.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency, formatDateTime, formatTime, humanize } from '../utils/format.js';

export const PAYMENT_TOKENS = [
  { value: 'tok_visa', label: 'Visa (approves)', help: 'Payment succeeds; the order moves to CONFIRMED and confirmation messages are sent.' },
  { value: 'tok_declined', label: 'Card declined', help: 'Gateway declines the card (402). The order stays PAYMENT_PENDING so you can retry.' },
  { value: 'tok_insufficient_funds', label: 'Insufficient funds', help: 'Decline with an insufficient-funds reason (402). The order can be paid again.' },
  { value: 'tok_gateway_error', label: 'Gateway error', help: 'Simulates a payment-provider outage: the attempt is recorded as FAILED (402) and the order stays payable.' },
];

const POLL_INTERVAL_MS = 1000;
const POLL_DURATION_MS = 12000;
const CONFIRMED_OR_LATER = ['CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

function PaymentStatus({ status }) {
  const cls = { SUCCEEDED: 'badge-success', FAILED: 'badge-danger', VOIDED: 'badge-muted', PENDING: 'badge-warn' }[status] || 'badge-muted';
  return <span className={`badge ${cls}`}>{humanize(status)}</span>;
}

/** Polls GET /api/notifications until messages for this order arrive (async RabbitMQ consumer). */
function useOrderNotifications(orderId) {
  const [state, setState] = useState({ phase: 'idle', items: [] }); // idle | waiting | done | timeout
  const timer = useRef(null);

  const stop = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
  };

  const check = useCallback(async () => {
    const all = await listNotifications();
    return (all || []).filter((n) => String(n.orderId) === String(orderId));
  }, [orderId]);

  const start = useCallback(() => {
    stop();
    const deadline = Date.now() + POLL_DURATION_MS;
    setState({ phase: 'waiting', items: [] });
    const tick = async () => {
      let items = [];
      try {
        items = await check();
      } catch {
        /* keep polling */
      }
      const channels = new Set(items.map((n) => n.channel));
      if (channels.has('EMAIL') && channels.has('SMS')) {
        setState({ phase: 'done', items });
        timer.current = null;
      } else if (Date.now() >= deadline) {
        setState({ phase: items.length ? 'done' : 'timeout', items });
        timer.current = null;
      } else {
        setState({ phase: 'waiting', items });
        timer.current = setTimeout(tick, POLL_INTERVAL_MS);
      }
    };
    timer.current = setTimeout(tick, 400);
  }, [check]);

  // one-off lookup (e.g. when opening an already-confirmed order)
  const loadOnce = useCallback(async () => {
    try {
      const items = await check();
      if (items.length) setState({ phase: 'done', items });
    } catch {
      /* ignore */
    }
  }, [check]);

  useEffect(() => stop, []);
  return { ...state, start, loadOnce };
}

export default function OrderDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const toast = useToast();
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [payments, setPayments] = useState([]);
  const [token, setToken] = useState('tok_visa');
  const [paying, setPaying] = useState(false);
  const [payResult, setPayResult] = useState(null);
  const [cancelling, setCancelling] = useState(false);
  const notifications = useOrderNotifications(id);
  const { loadOnce } = notifications;

  const loadPayments = useCallback(() => listPayments(id).then((p) => setPayments(p || [])).catch(() => {}), [id]);

  useEffect(() => {
    setOrder(null);
    setError(null);
    setPayResult(null);
    getOrder(id)
      .then((o) => {
        setOrder(o);
        if (CONFIRMED_OR_LATER.includes(o.status)) loadOnce();
      })
      .catch(setError);
    loadPayments();
  }, [id, loadPayments, loadOnce]);

  const onPay = async () => {
    setPaying(true);
    setPayResult(null);
    try {
      const { payment, replayed } = await pay(order.id, token);
      setPayResult({ ok: true, payment, replayed });
      toast.success(replayed ? 'This order was already paid.' : `Payment ${payment?.transactionReference || ''} succeeded.`, 'Payment');
      setOrder(await getOrder(order.id));
      notifications.start();
    } catch (err) {
      setPayResult({ ok: false, error: err, payment: err.data });
      if (err.status !== 402) toast.error(err);
      getOrder(order.id).then(setOrder).catch(() => {});
    } finally {
      setPaying(false);
      loadPayments();
    }
  };

  const onCancel = async () => {
    if (!window.confirm(`Cancel order #${order.id}? Reserved stock will be released.`)) return;
    setCancelling(true);
    try {
      setOrder(await cancelOrder(order.id));
      toast.success(`Order #${order.id} cancelled.`);
      loadPayments();
    } catch (err) {
      toast.error(err);
      getOrder(order.id).then(setOrder).catch(() => {});
    } finally {
      setCancelling(false);
    }
  };

  if (error) {
    return (
      <div>
        <Link to="/orders" className="back-link">← All orders</Link>
        <ErrorBanner error={error} />
      </div>
    );
  }
  if (!order) return <Spinner label="Loading order…" />;

  const selected = PAYMENT_TOKENS.find((t) => t.value === token);

  return (
    <div>
      <Link to="/orders" className="back-link">← All orders</Link>
      {location.state?.justPlaced && order.payable && (
        <div className="alert alert-success">
          {location.state.replayed
            ? 'We found the order from your earlier attempt — no duplicate was created.'
            : 'Order placed! Stock is reserved — complete payment below to confirm it.'}
        </div>
      )}

      <div className="page-head">
        <div>
          <h1>Order #{order.id}</h1>
          <p className="muted">Placed {formatDateTime(order.createdAt)}</p>
        </div>
        <div className="head-actions">
          <StatusBadge status={order.status} />
          {order.cancellable && (
            <button className="btn btn-danger-outline" onClick={onCancel} disabled={cancelling || paying}>
              {cancelling ? 'Cancelling…' : 'Cancel order'}
            </button>
          )}
        </div>
      </div>

      <div className="card">
        <h2 className="card-title">Status</h2>
        <StatusTimeline order={order} />
      </div>

      <div className="order-layout">
        <div className="card table-card">
          <h2 className="card-title pad">Items</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Product</th>
                <th className="num">Unit price</th>
                <th className="num">Qty</th>
                <th className="num">Line total</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((i) => (
                <tr key={i.id}>
                  <td data-label="Product"><Link to={`/products/${i.productId}`}>{i.productName}</Link></td>
                  <td data-label="Unit price" className="num">{formatCurrency(i.unitPrice)}</td>
                  <td data-label="Qty" className="num">{i.quantity}</td>
                  <td data-label="Line total" className="num">{formatCurrency(i.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={3} className="num strong">Total</td>
                <td className="num strong total-cell">{formatCurrency(order.totalAmount)}</td>
              </tr>
            </tfoot>
          </table>
        </div>

        <div className="side-stack">
          {order.payable && (
            <div className="card pay-panel">
              <h2 className="card-title">Pay {formatCurrency(order.totalAmount)}</h2>
              <label htmlFor="token" className="label">Test payment token</label>
              <select id="token" value={token} onChange={(e) => setToken(e.target.value)} disabled={paying}>
                {PAYMENT_TOKENS.map((t) => (
                  <option key={t.value} value={t.value}>{t.label} — {t.value}</option>
                ))}
              </select>
              <p className="muted small">{selected?.help}</p>
              <button className="btn btn-primary btn-block" onClick={onPay} disabled={paying || cancelling}>
                {paying ? 'Processing…' : `Pay ${formatCurrency(order.totalAmount)}`}
              </button>
              <ul className="token-help small muted">
                {PAYMENT_TOKENS.map((t) => (
                  <li key={t.value}><code>{t.value}</code> — {t.help}</li>
                ))}
              </ul>
            </div>
          )}

          {payResult && !payResult.ok && (
            <div className="alert alert-error" role="alert">
              <div>
                <strong>{payResult.error.status === 402 ? 'Payment declined' : payResult.error.code}:</strong>{' '}
                {payResult.payment?.failureReason || payResult.error.message}
                {payResult.error.status === 402 && <div className="small">Your order is still awaiting payment — you can try again.</div>}
                {payResult.error.requestId && <div className="request-id">Request ID: {payResult.error.requestId}</div>}
              </div>
            </div>
          )}
          {payResult?.ok && (
            <div className="alert alert-success">
              <div>
                <strong>{payResult.replayed ? 'Already paid' : 'Payment succeeded'}</strong>
                {payResult.replayed && <div className="small">This order was already paid — showing the original payment.</div>}
                {payResult.payment?.transactionReference && <div className="mono small">Ref {payResult.payment.transactionReference}</div>}
              </div>
            </div>
          )}

          {notifications.phase !== 'idle' && (
            <div className={`card notify-panel phase-${notifications.phase}`}>
              {notifications.phase === 'waiting' && (
                <div className="loading inline">
                  <span className="spinner" aria-hidden="true" />
                  <span>Waiting for confirmation messages (async via RabbitMQ)…</span>
                </div>
              )}
              {notifications.phase === 'done' && (
                <div className="strong success-text">✓ Confirmation email &amp; SMS sent</div>
              )}
              {notifications.phase === 'timeout' && (
                <div className="muted">No confirmation messages yet — check the Notifications page shortly.</div>
              )}
              {notifications.items.length > 0 && (
                <ul className="notify-list small">
                  {notifications.items.map((n) => (
                    <li key={n.id}>
                      <span className={`badge ${n.channel === 'EMAIL' ? 'badge-info' : 'badge-violet'}`}>{n.channel}</span>
                      <span className="mono">{n.recipient}</span>
                      <span className="muted">{formatTime(n.createdAt)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          <div className="card">
            <h2 className="card-title">Payment attempts</h2>
            {payments.length === 0 ? (
              <p className="muted small">No payment attempts yet.</p>
            ) : (
              <ul className="payment-list">
                {payments.map((p) => (
                  <li key={p.id}>
                    <div className="payment-row">
                      <PaymentStatus status={p.status} />
                      <span className="strong">{formatCurrency(p.amount)}</span>
                      <span className="muted small">{formatDateTime(p.createdAt)}</span>
                    </div>
                    {p.transactionReference && <div className="mono small muted">Ref {p.transactionReference}</div>}
                    {p.failureReason && <div className="small danger-text">{p.failureReason}</div>}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
