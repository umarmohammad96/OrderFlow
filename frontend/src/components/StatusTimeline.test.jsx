import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import StatusTimeline from './StatusTimeline.jsx';

describe('StatusTimeline', () => {
  it('marks steps done/current/upcoming from the order status', () => {
    const order = {
      status: 'CONFIRMED',
      createdAt: '2026-01-01T10:00:00Z',
      statusHistory: [
        { from: 'CREATED', to: 'PAYMENT_PENDING', changedAt: '2026-01-01T10:00:01Z' },
        { from: 'PAYMENT_PENDING', to: 'CONFIRMED', changedAt: '2026-01-01T10:01:00Z' },
      ],
    };
    const { container } = render(<StatusTimeline order={order} />);
    expect(container.querySelectorAll('.timeline-step.done')).toHaveLength(2);
    expect(container.querySelector('.timeline-step.current').textContent).toContain('Confirmed');
    expect(screen.getByText('Delivered').closest('li').className).toContain('upcoming');
  });

  it('ends with a cancelled step for cancelled orders', () => {
    const order = {
      status: 'CANCELLED',
      createdAt: '2026-01-01T10:00:00Z',
      statusHistory: [
        { from: 'CREATED', to: 'PAYMENT_PENDING', changedAt: '2026-01-01T10:00:01Z' },
        { from: 'PAYMENT_PENDING', to: 'CANCELLED', changedAt: '2026-01-01T10:02:00Z' },
      ],
    };
    const { container } = render(<StatusTimeline order={order} />);
    const steps = container.querySelectorAll('.timeline-step');
    expect(steps[steps.length - 1].className).toContain('cancelled');
    expect(screen.queryByText('Shipped')).toBeNull();
  });
});
