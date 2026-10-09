import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import client, { ApiError, handleResponseError, setUnauthorizedHandler, toApiError } from './client.js';
import { loadAuth, saveAuth } from '../utils/authStorage.js';

const envelopeError = (status, error, extra = {}) => ({
  config: { url: '/orders' },
  response: {
    status,
    statusText: 'X',
    headers: {},
    data: { success: false, data: null, error, requestId: 'req-123', timestamp: '2026-01-01T00:00:00Z', ...extra },
  },
});

describe('toApiError', () => {
  it('unwraps the server envelope', () => {
    const err = toApiError(envelopeError(409, { code: 'OUT_OF_STOCK', message: 'Insufficient stock for product 7' }));
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('OUT_OF_STOCK');
    expect(err.message).toBe('Insufficient stock for product 7');
    expect(err.requestId).toBe('req-123');
  });

  it('exposes validation details as field errors', () => {
    const err = toApiError(
      envelopeError(400, {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        details: [
          { field: 'email', message: 'must be a well-formed email address' },
          { field: 'password', message: 'size must be between 8 and 72' },
        ],
      }),
    );
    expect(err.details).toHaveLength(2);
    expect(err.fieldErrors()).toEqual({
      email: 'must be a well-formed email address',
      password: 'size must be between 8 and 72',
    });
  });

  it('keeps envelope data (e.g. the FAILED payment on 402)', () => {
    const payment = { id: 9, status: 'FAILED', failureReason: 'Card declined' };
    const err = toApiError(envelopeError(402, { code: 'PAYMENT_DECLINED', message: 'declined' }, { data: payment }));
    expect(err.data).toEqual(payment);
  });

  it('maps network failures and timeouts', () => {
    expect(toApiError({ message: 'Network Error' }).code).toBe('NETWORK_ERROR');
    expect(toApiError({ code: 'ECONNABORTED' }).code).toBe('TIMEOUT');
    expect(toApiError({ code: 'ECONNABORTED' }).status).toBe(0);
  });

  it('falls back to HTTP status when body is not an envelope', () => {
    const err = toApiError({ response: { status: 502, statusText: 'Bad Gateway', headers: {}, data: '<html>' } });
    expect(err.code).toBe('HTTP_502');
    expect(err.message).toBe('Bad Gateway');
  });
});

describe('response interceptor', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });
  afterEach(() => setUnauthorizedHandler(() => {}));

  it('rejects with ApiError and on 401 clears auth and calls the handler', async () => {
    saveAuth({ accessToken: 'abc', user: { id: 1 } });
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    const p = handleResponseError(envelopeError(401, { code: 'UNAUTHORIZED', message: 'Authentication required' }));
    await expect(p).rejects.toMatchObject({ status: 401, code: 'UNAUTHORIZED' });
    expect(loadAuth()).toBeNull();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not log out on 401 from the login endpoint (bad credentials)', async () => {
    saveAuth({ accessToken: 'abc', user: { id: 1 } });
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    const err = { ...envelopeError(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }), config: { url: '/auth/login' } };
    await expect(handleResponseError(err)).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' });
    expect(handler).not.toHaveBeenCalled();
    expect(loadAuth()).not.toBeNull();
  });

  it('is wired into the axios instance and attaches the bearer token', async () => {
    saveAuth({ accessToken: 'tok-1', user: { id: 1 } });
    let seenAuth;
    const adapter = (config) => {
      seenAuth = config.headers.Authorization;
      return Promise.reject(
        Object.assign(new Error('fail'), {
          config,
          response: { status: 404, headers: {}, data: { success: false, error: { code: 'NOT_FOUND', message: 'Product 99 not found' }, requestId: 'r-9' } },
        }),
      );
    };
    await expect(client.get('/products/99', { adapter })).rejects.toMatchObject({ code: 'NOT_FOUND', requestId: 'r-9', status: 404 });
    expect(seenAuth).toBe('Bearer tok-1');
  });
});
