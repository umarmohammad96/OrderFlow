import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';

const ToastContext = createContext(null);

let nextId = 1;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef(new Map());

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) clearTimeout(timer);
    timers.current.delete(id);
  }, []);

  const push = useCallback(
    ({ type = 'info', title, message, requestId, timeout }) => {
      const id = nextId++;
      setToasts((list) => [...list.slice(-4), { id, type, title, message, requestId }]);
      const ms = timeout ?? (type === 'error' ? 8000 : 4000);
      if (ms > 0) timers.current.set(id, setTimeout(() => dismiss(id), ms));
      return id;
    },
    [dismiss],
  );

  const api = useMemo(
    () => ({
      push,
      dismiss,
      success: (message, title) => push({ type: 'success', message, title }),
      info: (message, title) => push({ type: 'info', message, title }),
      /** Shows an ApiError (or any Error) including the requestId for support. */
      error: (err, title) =>
        push({
          type: 'error',
          title: title || (err?.code && err.code !== 'UNKNOWN_ERROR' ? err.code : 'Error'),
          message: err?.message || String(err),
          requestId: err?.requestId,
        }),
    }),
    [push, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-region" role="region" aria-live="polite" aria-label="Notifications">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`} role={t.type === 'error' ? 'alert' : 'status'}>
            <div className="toast-body">
              {t.title && <div className="toast-title">{t.title}</div>}
              <div className="toast-message">{t.message}</div>
              {t.requestId && <div className="toast-request">Request ID: {t.requestId}</div>}
            </div>
            <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Dismiss">
              ×
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
