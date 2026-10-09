import client, { unwrap } from './client.js';

/**
 * Places an order. The Idempotency-Key must be supplied by the caller so that a retry
 * after a network failure re-sends the SAME key (see utils/idempotency.js).
 */
export async function placeOrder(items, idempotencyKey) {
  const res = await client.post('/orders', { items }, { headers: { 'Idempotency-Key': idempotencyKey } });
  return { order: unwrap(res), replayed: String(res.headers?.['idempotent-replayed']) === 'true' };
}

export const listOrders = ({ page = 0, size = 10 } = {}) => client.get('/orders', { params: { page, size } }).then(unwrap);
export const getOrder = (id) => client.get(`/orders/${id}`).then(unwrap);
export const cancelOrder = (id) => client.post(`/orders/${id}/cancel`).then(unwrap);

export function adminListOrders({ status, page = 0, size = 20 } = {}) {
  const params = { page, size };
  if (status) params.status = status;
  return client.get('/admin/orders', { params }).then(unwrap);
}
export const adminUpdateStatus = (id, status) => client.patch(`/admin/orders/${id}/status`, { status }).then(unwrap);
