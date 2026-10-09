export default function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  const pages = [];
  const start = Math.max(0, Math.min(page - 2, totalPages - 5));
  const end = Math.min(totalPages, start + 5);
  for (let i = start; i < end; i++) pages.push(i);

  return (
    <nav className="pagination" aria-label="Pagination">
      <button className="btn btn-ghost btn-sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        ‹ Prev
      </button>
      {pages.map((p) => (
        <button
          key={p}
          className={`btn btn-sm ${p === page ? 'btn-primary' : 'btn-ghost'}`}
          aria-current={p === page ? 'page' : undefined}
          onClick={() => onChange(p)}
        >
          {p + 1}
        </button>
      ))}
      <button className="btn btn-ghost btn-sm" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Next ›
      </button>
    </nav>
  );
}
