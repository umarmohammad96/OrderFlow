/**
 * Idempotency-Key manager for checkout.
 *
 * Rules:
 *  - One key per checkout attempt, generated with crypto.randomUUID().
 *  - Retrying the SAME cart contents (e.g. after a network error / timeout) reuses the same key,
 *    so the server can replay the original result instead of creating a duplicate order.
 *  - A new key is generated only when the cart contents change, or after a successful checkout.
 *
 * The current key is optionally mirrored to sessionStorage so a page reload mid-retry still reuses it.
 */

export function generateKey() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for non-secure contexts (randomUUID requires https or localhost).
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) crypto.getRandomValues(bytes);
  else for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Stable fingerprint of the order payload, independent of item ordering. */
export function cartFingerprint(items) {
  return (items || [])
    .map((i) => `${i.productId}:${i.quantity}`)
    .sort()
    .join('|');
}

export function createIdempotencyKeyManager({ storage, storageKey = 'orderflow.checkoutKey', keyFactory = generateKey } = {}) {
  let state = null; // { key, fingerprint }

  const read = () => {
    if (state || !storage) return state;
    try {
      const raw = storage.getItem(storageKey);
      state = raw ? JSON.parse(raw) : null;
    } catch {
      state = null;
    }
    return state;
  };
  const write = (next) => {
    state = next;
    if (!storage) return;
    try {
      if (next) storage.setItem(storageKey, JSON.stringify(next));
      else storage.removeItem(storageKey);
    } catch {
      /* ignore storage failures */
    }
  };

  return {
    /** Returns the key for this cart; reuses the pending key if the cart is unchanged. */
    keyFor(items) {
      const fingerprint = cartFingerprint(items);
      const current = read();
      if (current && current.fingerprint === fingerprint) return current.key;
      const key = keyFactory();
      write({ key, fingerprint });
      return key;
    },
    /** The pending key, if any (without generating one). */
    peek() {
      return read()?.key || null;
    },
    /** Call after a successful checkout (or when the key must not be reused). */
    reset() {
      write(null);
    },
  };
}

const sessionStore = (() => {
  try {
    return typeof window !== 'undefined' ? window.sessionStorage : undefined;
  } catch {
    return undefined;
  }
})();

/** App-wide checkout key manager. */
export const checkoutKeys = createIdempotencyKeyManager({ storage: sessionStore });
