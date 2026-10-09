import client, { unwrap } from './client.js';

export const getCart = () => client.get('/cart').then(unwrap);
export const addItem = (productId, quantity) => client.post('/cart/items', { productId, quantity }).then(unwrap);
export const updateItem = (itemId, quantity) => client.patch(`/cart/items/${itemId}`, { quantity }).then(unwrap);
export const removeItem = (itemId) => client.delete(`/cart/items/${itemId}`).then(unwrap);
