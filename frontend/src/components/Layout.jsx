import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { useCart } from '../context/CartContext.jsx';

function UserMenu() {
  const { user, logout, isAdmin } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDoc = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const initials = (user?.name || user?.email || '?')
    .split(/\s+/)
    .map((p) => p[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <div className="user-menu" ref={ref}>
      <button className="user-trigger" onClick={() => setOpen((o) => !o)} aria-haspopup="menu" aria-expanded={open}>
        <span className="avatar" aria-hidden="true">{initials}</span>
        <span className="user-name">{user?.name}</span>
        <span className="caret" aria-hidden="true">▾</span>
      </button>
      {open && (
        <div className="user-dropdown" role="menu">
          <div className="user-dropdown-head">
            <div className="strong">{user?.name}</div>
            <div className="muted small">{user?.email}</div>
            {isAdmin && <span className="badge badge-admin">Admin</span>}
          </div>
          <Link to="/orders" role="menuitem" onClick={() => setOpen(false)}>My orders</Link>
          <Link to="/notifications" role="menuitem" onClick={() => setOpen(false)}>Notifications</Link>
          <button role="menuitem" className="danger-link" onClick={logout}>Log out</button>
        </div>
      )}
    </div>
  );
}

export default function Layout() {
  const { isAuthenticated, isAdmin } = useAuth();
  const { count } = useCart();
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();

  useEffect(() => setNavOpen(false), [location.pathname]);

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="container header-inner">
          <Link to="/" className="brand">
            <span className="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" width="18" height="18"><path d="M4 7h16l-2 11H6z" fill="currentColor" /><path d="M9 7a3 3 0 0 1 6 0" stroke="currentColor" strokeWidth="2" fill="none" /></svg>
            </span>
            OrderFlow
          </Link>
          <button className="nav-toggle" onClick={() => setNavOpen((o) => !o)} aria-label="Toggle navigation" aria-expanded={navOpen}>
            <span /><span /><span />
          </button>
          <nav className={`main-nav ${navOpen ? 'open' : ''}`}>
            <NavLink to="/" end>Products</NavLink>
            <NavLink to="/cart" className="cart-link">
              Cart
              {count > 0 && <span className="count-badge" aria-label={`${count} items`}>{count > 99 ? '99+' : count}</span>}
            </NavLink>
            <NavLink to="/orders">Orders</NavLink>
            <NavLink to="/notifications">Notifications</NavLink>
            {isAdmin && <NavLink to="/admin">Admin</NavLink>}
            <div className="nav-spacer" />
            {isAuthenticated ? (
              <UserMenu />
            ) : (
              <div className="auth-links">
                <NavLink to="/login">Sign in</NavLink>
                <Link to="/register" className="btn btn-primary btn-sm">Create account</Link>
              </div>
            )}
          </nav>
        </div>
      </header>
      <main className="container main">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="container muted small">OrderFlow · Spring Boot + React commerce demo · payments use test tokens only</div>
      </footer>
    </div>
  );
}
