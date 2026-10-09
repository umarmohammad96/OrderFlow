import axios from 'axios';
import { clearAuth, getToken } from '../utils/authStorage.js';

/** Normalised error thrown by every API call. */
export class ApiError extends Error {
  constructor({ status, code, message, details, requestId, data }) {
    super(message || 'Request failed');
    this.name = 'ApiError';
    this.status = status ?? 0;
    this.code = code || 'UNKNOWN_ERROR';
    this.details = Array.isArray(details) ? details : [];
    this.requestId = requestId || null;
    this.data = data ?? null; // e.g. the FAILED payment on 402 PAYMENT_DECLINED
  }

  /** Map of field -> message for VALIDATION_FAILED errors. */
  fieldErrors() {
    return this.details.reduce((acc, d) => {
      if (d && d.field && !acc[d.field]) acc[d.field] = d.message;
      return acc;
    }, {});
  }
}

/** Converts an axios error into an ApiError using the server envelope when present. */
export function toApiError(error) {
  if (error instanceof ApiError) return error;
  const response = error?.response;
  if (!response) {
    const timeout = error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT';
    return new ApiError({
      status: 0,
      code: timeout ? 'TIMEOUT' : 'NETWORK_ERROR',
      message: timeout
        ? 'The server took too long to respond. Please try again.'
        : 'Could not reach the server. Check your connection and try again.',
    });
  }
  const body = response.data && typeof response.data === 'object' ? response.data : {};
  const err = body.error || {};
  return new ApiError({
    status: response.status,
    code: err.code || `HTTP_${response.status}`,
    message: err.message || response.statusText || `Request failed with status ${response.status}`,
    details: err.details,
    requestId: body.requestId || response.headers?.['x-request-id'] || null,
    data: body.data ?? null,
  });
}

let unauthorizedHandler = () => {
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.assign(`/login?next=${next}`);
  }
};

/** Lets AuthContext take over 401 handling (clear React state + router navigation). */
export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

// Credential endpoints legitimately return 401 (INVALID_CREDENTIALS); don't treat that as a session expiry.
const AUTH_ENDPOINTS = ['/auth/login', '/auth/register'];

export function handleResponseError(error) {
  const apiError = toApiError(error);
  const url = error?.config?.url || '';
  if (apiError.status === 401 && !AUTH_ENDPOINTS.some((p) => url.includes(p))) {
    clearAuth();
    try {
      unauthorizedHandler(apiError);
    } catch {
      /* ignore */
    }
  }
  return Promise.reject(apiError);
}

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
});

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use((response) => response, handleResponseError);

/** Returns the `data` field of a successful envelope. */
export const unwrap = (response) => response?.data?.data ?? null;

export default client;
