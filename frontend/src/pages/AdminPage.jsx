import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import * as catalog from '../api/catalog.js';
import { adminListOrders, adminUpdateStatus } from '../api/orders.js';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import FormField from '../components/FormField.jsx';
import Pagination from '../components/Pagination.jsx';
import StatusBadge, { StockBadge } from '../components/StatusBadge.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency, formatDateTime, humanize } from '../utils/format.js';

export const NEXT_STATUS = { CONFIRMED: 'PROCESSING', PROCESSING: 'SHIPPED', SHIPPED: 'DELIVERED' };
const ORDER_STATUSES = ['CREATED', 'PAYMENT_PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
const PRICE_RE = /^\d{1,8}(\.\d{1,2})?$/;
const priceText = (v) => (Number.isFinite(Number(v)) ? Number(v).toFixed(2) : String(v ?? ''));

/* ---------------- Categories ---------------- */
function CategoryForm({ onCreated }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    if (!name.trim()) return setError('Name is required');
    setBusy(true);
    setError(null);
    try {
      const c = await catalog.createCategory(name.trim());
      toast.success(`Category “${c.name}” created`);
      setName('');
      onCreated();
    } catch (err) {
      setError(err.fieldErrors().name || err.message);
      if (err.code !== 'VALIDATION_FAILED') toast.error(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card admin-form" onSubmit={submit} noValidate>
      <h2 className="card-title">New category</h2>
      <FormField label="Name" id="cat-name" error={error}>
        <input id="cat-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Garden" />
      </FormField>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create category'}</button>
    </form>
  );
}

/* ---------------- Create product ---------------- */
const EMPTY_PRODUCT = { name: '', description: '', price: '', categoryId: '', initialStock: '0' };

function ProductForm({ categories, onCreated }) {
  const toast = useToast();
  const [form, setForm] = useState(EMPTY_PRODUCT);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const validate = () => {
    const e = {};
    if (!form.name.trim()) e.name = 'Name is required';
    if (!PRICE_RE.test(form.price) || Number(form.price) <= 0) e.price = 'Enter a positive price (max 2 decimals)';
    if (!form.categoryId) e.categoryId = 'Pick a category';
    if (!/^\d+$/.test(form.initialStock)) e.initialStock = 'Whole number ≥ 0';
    return e;
  };

  const submit = async (ev) => {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    try {
      const p = await catalog.createProduct({
        name: form.name.trim(),
        description: form.description.trim(),
        price: Number(form.price),
        categoryId: Number(form.categoryId),
        initialStock: Number(form.initialStock),
      });
      toast.success(`Product “${p.name}” created`);
      setForm(EMPTY_PRODUCT);
      onCreated(p);
    } catch (err) {
      if (err.code === 'VALIDATION_FAILED') setErrors(err.fieldErrors());
      toast.error(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card admin-form" onSubmit={submit} noValidate>
      <h2 className="card-title">New product</h2>
      <div className="form-grid">
        <FormField label="Name" id="p-name" error={errors.name}>
          <input id="p-name" value={form.name} onChange={set('name')} />
        </FormField>
        <FormField label="Category" id="p-cat" error={errors.categoryId}>
          <select id="p-cat" value={form.categoryId} onChange={set('categoryId')}>
            <option value="">Select…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
        </FormField>
        <FormField label="Price (USD)" id="p-price" error={errors.price}>
          <input id="p-price" inputMode="decimal" value={form.price} onChange={set('price')} placeholder="19.99" />
        </FormField>
        <FormField label="Initial stock" id="p-stock" error={errors.initialStock}>
          <input id="p-stock" inputMode="numeric" value={form.initialStock} onChange={set('initialStock')} />
        </FormField>
      </div>
      <FormField label="Description" id="p-desc" error={errors.description}>
        <textarea id="p-desc" rows={3} value={form.description} onChange={set('description')} />
      </FormField>
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create product'}</button>
    </form>
  );
}

/* ---------------- Product row ---------------- */
function ProductRow({ product, onChange }) {
  const toast = useToast();
  const [price, setPrice] = useState(priceText(product.price));
  const [restockQty, setRestockQty] = useState('');
  const [busy, setBusy] = useState(null);

  useEffect(() => setPrice(priceText(product.price)), [product.price]);

  const save = async (patch, label) => {
    setBusy(label);
    try {
      const updated = await catalog.updateProduct(product.id, {
        name: product.name,
        description: product.description,
        price: Number(product.price),
        categoryId: product.categoryId,
        active: product.active,
        ...patch,
      });
      const fresh = await catalog.getProduct(product.id).catch(() => null);
      onChange({ ...product, ...updated, ...(fresh || {}) });
      toast.success(`${product.name}: ${label} saved`);
    } catch (err) {
      toast.error(err);
      setPrice(priceText(product.price));
    } finally {
      setBusy(null);
    }
  };

  const savePrice = () => {
    if (!PRICE_RE.test(price) || Number(price) <= 0) {
      toast.error({ message: 'Enter a positive price with at most 2 decimals' }, 'Invalid price');
      return;
    }
    if (Number(price) === Number(product.price)) return;
    save({ price: Number(price) }, 'price');
  };

  const doRestock = async () => {
    const qty = Number(restockQty);
    if (!Number.isInteger(qty) || qty <= 0) {
      toast.error({ message: 'Restock quantity must be a positive whole number' }, 'Invalid quantity');
      return;
    }
    setBusy('restock');
    try {
      const inv = await catalog.restock(product.id, qty);
      const fresh = await catalog.getProduct(product.id).catch(() => null);
      onChange({ ...product, ...(fresh || {}), availableQuantity: inv?.availableQuantity ?? fresh?.availableQuantity ?? product.availableQuantity });
      setRestockQty('');
      toast.success(`${product.name}: +${qty} restocked`);
    } catch (err) {
      toast.error(err);
    } finally {
      setBusy(null);
    }
  };

  return (
    <tr className={product.active ? '' : 'row-muted'}>
      <td data-label="Product">
        <Link to={`/products/${product.id}`} className="strong">{product.name}</Link>
        <div className="muted small">#{product.id} · {product.categoryName}</div>
      </td>
      <td data-label="Price">
        <div className="inline-edit">
          <input
            aria-label={`Price for ${product.name}`}
            inputMode="decimal"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && savePrice()}
          />
          <button className="btn btn-sm btn-ghost" onClick={savePrice} disabled={busy !== null || Number(price) === Number(product.price)}>
            {busy === 'price' ? '…' : 'Save'}
          </button>
        </div>
      </td>
      <td data-label="Stock">
        <div className="stock-cell">
          <span className="strong">{product.availableQuantity ?? '—'}</span>
          <StockBadge product={product} />
        </div>
      </td>
      <td data-label="Restock">
        <div className="inline-edit">
          <input
            aria-label={`Restock quantity for ${product.name}`}
            inputMode="numeric"
            placeholder="+qty"
            value={restockQty}
            onChange={(e) => setRestockQty(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && doRestock()}
          />
          <button className="btn btn-sm btn-ghost" onClick={doRestock} disabled={busy !== null || !restockQty}>
            {busy === 'restock' ? '…' : 'Restock'}
          </button>
        </div>
      </td>
      <td data-label="Active">
        <label className="switch">
          <input
            type="checkbox"
            checked={!!product.active}
            disabled={busy !== null}
            onChange={(e) => save({ active: e.target.checked }, e.target.checked ? 'activation' : 'deactivation')}
          />
          <span className="slider" aria-hidden="true" />
          <span className="switch-label">{product.active ? 'Active' : 'Inactive'}</span>
        </label>
      </td>
    </tr>
  );
}

function ProductAdmin({ reloadKey }) {
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [extra, setExtra] = useState([]); // inactive products looked up by id (the public list hides them)
  const [error, setError] = useState(null);
  const [lookupId, setLookupId] = useState('');

  const load = useCallback(() => {
    setError(null);
    catalog.listProducts({ page, size: 10, sort: 'NEWEST', includeInactive: true }).then(setResult).catch(setError);
  }, [page]);

  useEffect(load, [load, reloadKey]);

  const replace = (p) => {
    setResult((r) => (r ? { ...r, content: r.content.map((x) => (x.id === p.id ? p : x)) } : r));
    setExtra((list) => list.map((x) => (x.id === p.id ? p : x)));
  };

  const lookup = async (e) => {
    e.preventDefault();
    if (!lookupId) return;
    try {
      const p = await catalog.getProduct(lookupId);
      setExtra((list) => [p, ...list.filter((x) => x.id !== p.id)]);
      setLookupId('');
    } catch (err) {
      toast.error(err);
    }
  };

  const shown = [...extra, ...(result?.content || []).filter((p) => !extra.some((x) => x.id === p.id))];

  return (
    <div className="card table-card">
      <div className="table-head pad">
        <h2 className="card-title">Products</h2>
        <form className="inline-edit" onSubmit={lookup}>
          <input
            aria-label="Find product by ID"
            placeholder="Find by ID (incl. inactive)"
            inputMode="numeric"
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value.replace(/\D/g, ''))}
          />
          <button className="btn btn-sm btn-ghost">Find</button>
        </form>
      </div>
      <ErrorBanner error={error} onRetry={load} />
      {!result && !error ? (
        <Spinner />
      ) : (
        <table className="table admin-table">
          <thead>
            <tr>
              <th>Product</th>
              <th>Price</th>
              <th>Available</th>
              <th>Restock</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((p) => (
              <ProductRow key={p.id} product={p} onChange={replace} />
            ))}
          </tbody>
        </table>
      )}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
      <p className="muted small pad">Deactivated products are hidden from the public catalog but listed here so they can be reactivated.</p>
    </div>
  );
}

/* ---------------- Orders ---------------- */
function OrderAdmin() {
  const toast = useToast();
  const [status, setStatus] = useState('CONFIRMED');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(() => {
    setError(null);
    adminListOrders({ status: status || undefined, page, size: 10 }).then(setResult).catch(setError);
  }, [status, page]);

  useEffect(load, [load]);

  const advance = async (order) => {
    const next = NEXT_STATUS[order.status];
    if (!next) return;
    setBusyId(order.id);
    try {
      await adminUpdateStatus(order.id, next);
      toast.success(`Order #${order.id} → ${humanize(next)}`);
      load();
    } catch (err) {
      toast.error(err);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="card table-card">
      <div className="table-head pad">
        <h2 className="card-title">Orders</h2>
        <select
          aria-label="Filter by status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All statuses</option>
          {ORDER_STATUSES.map((s) => (
            <option key={s} value={s}>{humanize(s)}</option>
          ))}
        </select>
      </div>
      <ErrorBanner error={error} onRetry={load} />
      {!result && !error ? (
        <Spinner />
      ) : result && result.content.length === 0 ? (
        <p className="muted pad">No orders with this status.</p>
      ) : (
        result && (
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Customer</th>
                <th>Placed</th>
                <th>Status</th>
                <th className="num">Total</th>
                <th aria-label="Actions" />
              </tr>
            </thead>
            <tbody>
              {result.content.map((o) => (
                <tr key={o.id}>
                  <td data-label="Order"><Link to={`/orders/${o.id}`} className="strong">#{o.id}</Link></td>
                  <td data-label="Customer" className="muted">User #{o.userId}</td>
                  <td data-label="Placed">{formatDateTime(o.createdAt)}</td>
                  <td data-label="Status"><StatusBadge status={o.status} /></td>
                  <td data-label="Total" className="num">{formatCurrency(o.totalAmount)}</td>
                  <td className="actions">
                    {NEXT_STATUS[o.status] ? (
                      <button className="btn btn-sm btn-primary" disabled={busyId === o.id} onClick={() => advance(o)}>
                        {busyId === o.id ? '…' : `Mark ${humanize(NEXT_STATUS[o.status]).toLowerCase()}`}
                      </button>
                    ) : (
                      <span className="muted small">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      )}
      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />}
    </div>
  );
}

/* ---------------- Page ---------------- */
export default function AdminPage() {
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'orders' ? 'orders' : 'catalog';
  const [categories, setCategories] = useState(null);
  const [catError, setCatError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  const loadCategories = useCallback(() => {
    catalog.listCategories().then(setCategories).catch(setCatError);
  }, []);
  useEffect(loadCategories, [loadCategories]);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Admin</h1>
          <p className="muted">Manage the catalog, inventory and order fulfilment.</p>
        </div>
      </div>
      <div className="tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'catalog'} className={tab === 'catalog' ? 'active' : ''} onClick={() => setParams({})}>
          Catalog &amp; inventory
        </button>
        <button role="tab" aria-selected={tab === 'orders'} className={tab === 'orders' ? 'active' : ''} onClick={() => setParams({ tab: 'orders' })}>
          Orders
        </button>
      </div>

      {tab === 'catalog' ? (
        <>
          <ErrorBanner error={catError} onRetry={loadCategories} />
          <div className="admin-forms">
            <CategoryForm onCreated={loadCategories} />
            {categories ? (
              <ProductForm categories={categories} onCreated={() => setReloadKey((k) => k + 1)} />
            ) : (
              <div className="card"><Spinner /></div>
            )}
          </div>
          {categories && categories.length === 0 ? (
            <EmptyState title="No categories yet">Create a category first, then add products to it.</EmptyState>
          ) : (
            <ProductAdmin reloadKey={reloadKey} />
          )}
        </>
      ) : (
        <OrderAdmin />
      )}
    </div>
  );
}
