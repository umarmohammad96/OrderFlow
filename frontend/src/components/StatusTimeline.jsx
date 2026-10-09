import { formatDateTime, humanize } from '../utils/format.js';

const HAPPY_PATH = ['CREATED', 'PAYMENT_PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

/**
 * Renders the order lifecycle. Reached steps are taken from statusHistory (with timestamps);
 * the remaining happy-path steps are shown as upcoming. A cancelled order ends with a CANCELLED step.
 */
export default function StatusTimeline({ order }) {
  const history = order?.statusHistory || [];
  const reachedAt = {};
  if (order?.createdAt) reachedAt.CREATED = order.createdAt;
  history.forEach((h) => {
    if (h.to) reachedAt[h.to] = h.changedAt;
  });

  const cancelled = order?.status === 'CANCELLED';
  let steps;
  if (cancelled) {
    const reached = HAPPY_PATH.filter((s) => reachedAt[s]);
    steps = [...reached, 'CANCELLED'];
  } else {
    steps = HAPPY_PATH;
  }
  const currentIndex = steps.indexOf(order?.status);

  return (
    <ol className="timeline">
      {steps.map((s, i) => {
        const state = i < currentIndex ? 'done' : i === currentIndex ? 'current' : 'upcoming';
        return (
          <li key={s} className={`timeline-step ${state} ${s === 'CANCELLED' ? 'cancelled' : ''}`}>
            <span className="timeline-dot" aria-hidden="true">{state === 'done' ? '✓' : i + 1}</span>
            <div className="timeline-label">
              <div className="strong">{humanize(s)}</div>
              <div className="muted small">{reachedAt[s] ? formatDateTime(reachedAt[s]) : state === 'upcoming' ? 'Pending' : ''}</div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
