export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorBanner({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="alert alert-error" role="alert">
      <div>
        <strong>{error.code && error.code !== 'UNKNOWN_ERROR' ? error.code : 'Error'}:</strong> {error.message}
        {error.requestId && <div className="request-id">Request ID: {error.requestId}</div>}
      </div>
      {onRetry && (
        <button className="btn btn-sm btn-ghost" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, children, action }) {
  return (
    <div className="card empty-state">
      <h2>{title}</h2>
      {children && <div className="muted">{children}</div>}
      {action && <div className="empty-action">{action}</div>}
    </div>
  );
}
