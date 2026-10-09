import client, { unwrap } from './client.js';

/** Returns { payment, replayed }. A 402 decline rejects with ApiError whose `.data` is the FAILED payment. */
export async function pay(orderId, paymentToken) {
  const res = await client.post(`/payments/${orderId}`, { paymentToken });
  return { payment: unwrap(res), replayed: res.status === 200 };
}

export const listPayments = (orderId) => client.get(`/payments/${orderId}`).then(unwrap);
