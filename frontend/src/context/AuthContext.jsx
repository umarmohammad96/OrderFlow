import { createContext, startTransition, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth.js';
import { setUnauthorizedHandler } from '../api/client.js';
import { clearAuth, loadAuth, saveAuth } from '../utils/authStorage.js';
import { checkoutKeys } from '../utils/idempotency.js';
import { useToast } from './ToastContext.jsx';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => loadAuth());
  const navigate = useNavigate();
  const toast = useToast();

  const establish = useCallback((auth) => {
    const next = { accessToken: auth.accessToken, tokenType: auth.tokenType, expiresAt: auth.expiresAt, user: auth.user };
    saveAuth(next);
    setSession(next);
    return next.user;
  }, []);

  const login = useCallback(async (email, password) => establish(await authApi.login({ email, password })), [establish]);
  const register = useCallback(
    async (name, email, password) => establish(await authApi.register({ name, email, password })),
    [establish],
  );

  const logout = useCallback(() => {
    clearAuth();
    checkoutKeys.reset();
    // Batch with the (transition-wrapped) navigation so protected pages don't redirect with ?next= first.
    startTransition(() => setSession(null));
    navigate('/login', { replace: true });
  }, [navigate]);

  // 401 from any API call: drop the session and send the user to /login.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setSession(null);
      checkoutKeys.reset();
      if (window.location.pathname !== '/login') {
        toast.info('Your session has expired. Please sign in again.');
        navigate(`/login?next=${encodeURIComponent(window.location.pathname + window.location.search)}`, { replace: true });
      }
    });
  }, [navigate, toast]);

  // Refresh the profile once on load so a role change / revoked token is noticed.
  useEffect(() => {
    if (!session) return;
    authApi
      .me()
      .then((user) => {
        if (!user) return;
        setSession((s) => {
          if (!s) return s;
          const next = { ...s, user };
          saveAuth(next);
          return next;
        });
      })
      .catch(() => {
        /* 401 handled globally */
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Expire the session client-side when the token's expiresAt passes.
  useEffect(() => {
    if (!session?.expiresAt) return undefined;
    const ms = Date.parse(session.expiresAt) - Date.now();
    if (!Number.isFinite(ms)) return undefined;
    const t = setTimeout(() => {
      clearAuth();
      setSession(null);
      toast.info('Your session has expired. Please sign in again.');
      navigate('/login', { replace: true });
    }, Math.max(0, Math.min(ms, 2 ** 31 - 1)));
    return () => clearTimeout(t);
  }, [session?.expiresAt, navigate, toast]);

  const value = useMemo(
    () => ({
      user: session?.user || null,
      token: session?.accessToken || null,
      isAuthenticated: Boolean(session?.accessToken),
      isAdmin: session?.user?.role === 'ADMIN',
      login,
      register,
      logout,
    }),
    [session, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
