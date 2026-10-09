import client, { unwrap } from './client.js';

export const register = (payload) => client.post('/auth/register', payload).then(unwrap);
export const login = (payload) => client.post('/auth/login', payload).then(unwrap);
export const me = () => client.get('/auth/me').then(unwrap);
