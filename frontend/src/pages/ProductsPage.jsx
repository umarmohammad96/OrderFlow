import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { listCategories, listProducts } from '../api/catalog.js';
import Pagination from '../components/Pagination.jsx';
import { EmptyState, ErrorBanner, Spinner } from '../components/Feedback.jsx';
import { StockBadge } from '../components/StatusBadge.jsx';
import { formatCurrency } from '../utils/format.js';

const SORTS = [
  { value: 'NEWEST', label: 'Newest' },
  { value: 'PRICE_ASC', label: 'Price: low to high' },
  { value: 'PRICE_DESC', label: 'Price: high to low' },
  { value: 'NAME', label: 'Name A–Z' },
];
const PAGE_SIZE = 12;

export default function ProductsPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q') || '';
  const categoryId = params.get('categoryId') || '';
  const sort = params.get('sort') || 'NEWEST';
  const page = Math.max(0, Number(params.get('page')) || 0);

  const [search, setSearch] = useState(q);
  const [categories, setCategories] = useState([]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [reloadTick, setReloadTick] = useState(0);

  const update = (patch) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => {
      if (v === '' || v === null || v === undefined || (k === 'page' && v === 0) || (k === 'sort' && v === 'NEWEST')) next.delete(k);
      else next.set(k, String(v));
    });
    setParams(next);
  };

  useEffect(() => {
    listCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  // keep the input in sync on back/forward navigation
  useEffect(() => setSearch(q), [q]);

  // debounce typing into the URL
  useEffect(() => {
    if (search === q) return undefined;
    const t = setTimeout(() => update({ q: search, page: 0 }), 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listProducts({ page, size: PAGE_SIZE, categoryId, q, sort })
      .then((r) => !cancelled && setResult(r))
      .catch((e) => !cancelled && setError(e))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [page, categoryId, q, sort, reloadTick]);

  const products = result?.content || [];

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Products</h1>
          <p className="muted">{result ? `${result.totalElements} product${result.totalElements === 1 ? '' : 's'}` : 'Browse the catalog'}</p>
        </div>
      </div>

      <div className="toolbar card">
        <input
          className="search-input"
          type="search"
          placeholder="Search products…"
          aria-label="Search products"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select aria-label="Category" value={categoryId} onChange={(e) => update({ categoryId: e.target.value, page: 0 })}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        <select aria-label="Sort" value={sort} onChange={(e) => update({ sort: e.target.value, page: 0 })}>
          {SORTS.map((s) => (
            <option key={s.value} value={s.value}>{s.label}</option>
          ))}
        </select>
      </div>

      <ErrorBanner error={error} onRetry={() => setReloadTick((t) => t + 1)} />

      {loading && !result ? (
        <Spinner label="Loading products…" />
      ) : products.length === 0 && !error ? (
        <EmptyState title="No products found">Try a different search or category.</EmptyState>
      ) : (
        <div className={`product-grid ${loading ? 'is-loading' : ''}`}>
          {products.map((p) => (
            <Link to={`/products/${p.id}`} key={p.id} className="card product-card">
              <div className="product-thumb" aria-hidden="true">{(p.name || '?').charAt(0)}</div>
              <div className="product-body">
                <div className="product-meta">
                  <span className="chip">{p.categoryName}</span>
                  <StockBadge product={p} />
                </div>
                <h3 className="product-name">{p.name}</h3>
                <p className="product-desc muted">{p.description}</p>
                <div className="product-price">{formatCurrency(p.price)}</div>
              </div>
            </Link>
          ))}
        </div>
      )}

      {result && <Pagination page={result.page} totalPages={result.totalPages} onChange={(p) => update({ page: p })} />}
    </div>
  );
}
