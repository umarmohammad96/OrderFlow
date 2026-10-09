import client, { unwrap } from './client.js';

export const listCategories = () => client.get('/categories').then(unwrap);
export const createCategory = (name) => client.post('/categories', { name }).then(unwrap);

export function listProducts({ page = 0, size = 12, categoryId, q, sort, includeInactive } = {}) {
  const params = { page, size };
  if (includeInactive) params.includeInactive = true;
  if (categoryId) params.categoryId = categoryId;
  if (q && q.trim()) params.q = q.trim();
  if (sort) params.sort = sort;
  return client.get('/products', { params }).then(unwrap);
}

export const getProduct = (id) => client.get(`/products/${id}`).then(unwrap);
export const createProduct = (payload) => client.post('/products', payload).then(unwrap);
export const updateProduct = (id, payload) => client.put(`/products/${id}`, payload).then(unwrap);
export const deactivateProduct = (id) => client.delete(`/products/${id}`).then(unwrap);

export const getInventory = (productId) => client.get(`/inventory/${productId}`).then(unwrap);
export const restock = (productId, quantity) =>
  client.post(`/inventory/${productId}/restock`, { quantity }).then(unwrap);
