import client, { unwrap } from './client.js';

export const listNotifications = () => client.get('/notifications').then(unwrap);
