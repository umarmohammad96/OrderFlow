import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as cartApi from '../api/cart.js';
import { placeOrder } from '../api/orders.js';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import QuantityStepper from '../components/QuantityStepper.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency } from '../utils/format.js';
import { cartFingerprint, checkoutKeys } from '../utils/idempotency.js';

const RETRYABLE = (err) => err && (err.status === 0 || err.status >= 500);

export default function CartPage() {
  const { cart, setCart, refresh } = useCart();
  const toast = useToast();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(!cart);
  const [loadError, setLoadError] = useState(null);
  const [pendingItem, setPendingItem] = useState(null);
  const [checkingOut, setCheckingOut] = useState(false);
  const [checkoutError, setCheckoutError] = useState(null);

  useEffect(() => {
    setLoading(true);
    cartApi
      .getCart()
      .then((c) => {
        setCart(c);
        setLoadError(null);
      })
      .catch(setLoadError)
      .finally(() => setLoading(false));
  }, [setCart]);

  const items = useMemo(() => cart?.items || [], [cart]);
  const orderItems = useMemo(() => items.map((i) => ({ productId: i.productId, quantity: i.quantity })), [items]);
  const fingerprint = cartFingerprint(orderItems);

  // Contents changed since the last failed attempt -> the old error no longer applies.
  useEffect(() => {
    setCheckoutError((e) => (e && e.fingerprint !== fingerprint ? null : e));
  }, [fingerprint]);

  const blocked = items.filter((i) => !i.purchasable || i.quantity > i.availableQuantity);

  const mutate = async (itemId, fn) => {
    setPendingItem(itemId);
    try {
      setCart(await fn());
    } catch (err) {
      toast.error(err);
      refresh();
    } finally {
      setPendingItem(null);
    }
  };

  const checkout = async () => {
    if (!orderItems.length) return;
    // Same cart contents => same key (safe retry); changed contents => new key.
    const key = checkoutKeys.keyFor(orderItems);
    setCheckingOut(true);
    setCheckoutError(null);
    try {
      const { order, replayed } = await placeOrder(orderItems, key);
      checkoutKeys.reset();
      await refresh(); // server removed the ordered products from the cart
      toast.success(replayed ? 'Your earlier order request was already received.' : `Order #${order.id} placed.`, replayed ? 'Order recovered' : 'Order placed');
      navigate(`/orders/${order.id}`, { state: { justPlaced: true, replayed } });
    } catch (err) {
      if (err.code === 'IDEMPOTENCY_KEY_REUSED') checkoutKeys.reset();
      if (err.code === 'OUT_OF_STOCK' || err.code === 'PRODUCT_UNAVAILABLE') refresh();
      setCheckoutError({ error: err, retryable: RETRYABLE(err), key, fingerprint });
    } finally {
      setCheckingOut(false);
    }
  };

  if (loading && !cart) return <Spinner label="Loading cart…" />;
  if (loadError && !cart) return <ErrorBanner error={loadError} onRetry={() => refresh()} />;

  if (!items.length) {
    return (
      <EmptyState title="Your cart is empty" action={<Link className="btn btn-primary" to="/">Browse products</Link>}>
        Add something from the catalog to get started.
      </EmptyState>
    );
  }

  const ce = checkoutError?.error;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Cart</h1>
          <p className="muted">{cart.totalQuantity} item{cart.totalQuantity === 1 ? '' : 's'}</p>
        </div>
      </div>

      <div className="cart-layout">
        <div className="card table-card">
          <table className="table cart-table">
            <thead>
              <tr>
                <th>Product</th>
                <th className="num">Price</th>
                <th>Quantity</th>
                <th className="num">Total</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {items.map((item) => {
                const short = item.quantity > item.availableQuantity;
                return (
                  <tr key={item.id} className={!item.purchasable || short ? 'row-warn' : ''}>
                    <td data-label="Product">
                      <Link to={`/products/${item.productId}`} className="strong">{item.productName}</Link>
                      {!item.purchasable && <div className="field-error">No longer available</div>}
                      {item.purchasable && short && (
                        <div className="field-error">Only {item.availableQuantity} in stock — reduce the quantity</div>
                      )}
                    </td>
                    <td data-label="Price" className="num">{formatCurrency(item.unitPrice)}</td>
                    <td data-label="Quantity">
                      <QuantityStepper
                        value={item.quantity}
                        disabled={pendingItem === item.id || checkingOut}
                        onChange={(q) => q !== item.quantity && mutate(item.id, () => cartApi.updateItem(item.id, q))}
                      />
                    </td>
                    <td data-label="Total" className="num strong">{formatCurrency(item.lineTotal)}</td>
                    <td className="actions">
                      <button
                        className="btn btn-ghost btn-sm danger-text"
                        disabled={pendingItem === item.id || checkingOut}
                        onClick={() => mutate(item.id, () => cartApi.removeItem(item.id))}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        <aside className="card summary-card">
          <h2>Summary</h2>
          <div className="summary-row"><span>Items</span><span>{cart.totalQuantity}</span></div>
          <div className="summary-row total"><span>Subtotal</span><span>{formatCurrency(cart.subtotal)}</span></div>
          <p className="muted small">Payment is taken on the order page after the order is placed.</p>

          {ce && (
            <div className={`alert ${checkoutError.retryable ? 'alert-warn' : 'alert-error'}`} role="alert">
              {ce.code === 'OUT_OF_STOCK' ? (
                <div>
                  <strong>Not enough stock.</strong> {ce.message}. We refreshed your cart with the latest availability — adjust the
                  highlighted items and try again.
                </div>
              ) : ce.code === 'PRODUCT_UNAVAILABLE' ? (
                <div>
                  <strong>A product is unavailable.</strong> {ce.message}. Remove it to continue.
                </div>
              ) : checkoutError.retryable ? (
                <div>
                  <strong>We couldn&apos;t confirm your order.</strong> {ce.message} Retrying is safe — we&apos;ll resend the same
                  request key, so you won&apos;t be charged or ordered twice.
                </div>
              ) : (
                <div>
                  <strong>{ce.code}:</strong> {ce.message}
                </div>
              )}
              {ce.requestId && <div className="request-id">Request ID: {ce.requestId}</div>}
            </div>
          )}

          {blocked.length > 0 && !ce && (
            <div className="alert alert-warn small">Some items can&apos;t be ordered as-is. Adjust or remove them to continue.</div>
          )}

          <button className="btn btn-primary btn-block btn-lg" onClick={checkout} disabled={checkingOut || pendingItem !== null || blocked.length > 0}>
            {checkingOut ? 'Placing order…' : checkoutError?.retryable ? 'Retry placing order' : 'Place order'}
          </button>
          {checkoutError?.retryable && (
            <div className="muted small mono key-note" title="Idempotency-Key reused for this retry">
              Idempotency-Key: {checkoutError.key.slice(0, 8)}…
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
