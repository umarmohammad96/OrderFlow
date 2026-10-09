import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { getProduct } from '../api/catalog.js';
import { ErrorBanner, Spinner } from '../components/Feedback.jsx';
import { StockBadge } from '../components/StatusBadge.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { formatCurrency, formatDateTime } from '../utils/format.js';
import QuantityStepper, { MAX_LINE_QTY } from '../components/QuantityStepper.jsx';

export default function ProductDetailPage() {
  const { id } = useParams();
  const { isAuthenticated } = useAuth();
  const { addItem } = useCart();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [product, setProduct] = useState(null);
  const [error, setError] = useState(null);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    setProduct(null);
    setError(null);
    getProduct(id).then(setProduct).catch(setError);
  }, [id]);

  if (error) {
    return (
      <div>
        <Link to="/" className="back-link">← Back to products</Link>
        <ErrorBanner error={error} />
      </div>
    );
  }
  if (!product) return <Spinner />;

  const purchasable = product.active !== false && product.inStock && product.availableQuantity > 0;
  const maxQty = Math.max(1, Math.min(MAX_LINE_QTY, product.availableQuantity || 1));

  const onAdd = async () => {
    if (!isAuthenticated) {
      navigate(`/login?next=${encodeURIComponent(location.pathname)}`);
      return;
    }
    setAdding(true);
    try {
      await addItem(product.id, qty);
      toast.push({
        type: 'success',
        title: 'Added to cart',
        message: `${qty} × ${product.name}`,
      });
    } catch (err) {
      toast.error(err);
    } finally {
      setAdding(false);
    }
  };

  return (
    <div>
      <Link to="/" className="back-link">← Back to products</Link>
      <div className="card product-detail">
        <div className="product-hero" aria-hidden="true">{product.name.charAt(0)}</div>
        <div className="product-detail-body">
          <div className="product-meta">
            <span className="chip">{product.categoryName}</span>
            <StockBadge product={product} />
          </div>
          <h1>{product.name}</h1>
          <div className="product-price large">{formatCurrency(product.price)}</div>
          <p className="product-description">{product.description}</p>
          <div className="buy-row">
            <QuantityStepper value={qty} onChange={setQty} max={maxQty} disabled={!purchasable || adding} />
            <button className="btn btn-primary" onClick={onAdd} disabled={!purchasable || adding}>
              {adding ? 'Adding…' : isAuthenticated ? 'Add to cart' : 'Sign in to add to cart'}
            </button>
          </div>
          <dl className="meta-list small muted">
            <div><dt>Available</dt><dd>{product.availableQuantity}</dd></div>
            <div><dt>SKU</dt><dd>#{product.id}</dd></div>
            <div><dt>Updated</dt><dd>{formatDateTime(product.updatedAt)}</dd></div>
          </dl>
        </div>
      </div>
    </div>
  );
}
