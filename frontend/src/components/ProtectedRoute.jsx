import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

export default function ProtectedRoute({ children, requireAdmin = false }) {
  const { isAuthenticated, isAdmin } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (requireAdmin && !isAdmin) {
    return (
      <div className="card empty-state">
        <h2>403 · Admins only</h2>
        <p className="muted">Your account doesn&apos;t have permission to view this page.</p>
      </div>
    );
  }
  return children;
}
