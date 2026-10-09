// Thin, exception-safe wrapper around localStorage for the auth session.
// localStorage can throw (private mode, blocked storage), so every access is guarded.
const KEY = 'orderflow.auth';

export function loadAuth() {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || !parsed.accessToken) return null;
    if (parsed.expiresAt && Date.parse(parsed.expiresAt) <= Date.now()) {
      clearAuth();
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveAuth(session) {
  try {
    window.localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    /* storage unavailable: session lives in memory only */
  }
}

export function clearAuth() {
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

export function getToken() {
  return loadAuth()?.accessToken || null;
}
