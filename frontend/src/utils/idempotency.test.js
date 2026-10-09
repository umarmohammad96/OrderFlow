import { describe, expect, it } from 'vitest';
import { cartFingerprint, createIdempotencyKeyManager, generateKey } from './idempotency.js';

function memoryStorage() {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  };
}

const counterFactory = () => {
  let n = 0;
  return () => `key-${String(++n).padStart(4, '0')}`;
};

describe('idempotency key manager', () => {
  const cart = [
    { productId: 1, quantity: 2 },
    { productId: 5, quantity: 1 },
  ];

  it('reuses the same key when retrying the same cart', () => {
    const m = createIdempotencyKeyManager({ keyFactory: counterFactory() });
    const first = m.keyFor(cart);
    expect(m.keyFor(cart)).toBe(first);
    expect(m.keyFor([...cart].reverse())).toBe(first); // order of lines doesn't matter
  });

  it('generates a new key when the cart contents change', () => {
    const m = createIdempotencyKeyManager({ keyFactory: counterFactory() });
    const first = m.keyFor(cart);
    const second = m.keyFor([{ productId: 1, quantity: 3 }, { productId: 5, quantity: 1 }]);
    expect(second).not.toBe(first);
  });

  it('generates a new key after reset (successful checkout)', () => {
    const m = createIdempotencyKeyManager({ keyFactory: counterFactory() });
    const first = m.keyFor(cart);
    m.reset();
    expect(m.peek()).toBeNull();
    expect(m.keyFor(cart)).not.toBe(first);
  });

  it('survives a reload through storage', () => {
    const storage = memoryStorage();
    const a = createIdempotencyKeyManager({ storage, keyFactory: counterFactory() });
    const key = a.keyFor(cart);
    const b = createIdempotencyKeyManager({ storage, keyFactory: () => 'should-not-be-used' });
    expect(b.keyFor(cart)).toBe(key);
  });

  it('tolerates a throwing storage', () => {
    const broken = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    };
    const m = createIdempotencyKeyManager({ storage: broken, keyFactory: counterFactory() });
    const k = m.keyFor(cart);
    expect(m.keyFor(cart)).toBe(k);
    expect(() => m.reset()).not.toThrow();
  });

  it('produces keys that satisfy the server format (8-100 chars of [A-Za-z0-9._:-])', () => {
    const key = generateKey();
    expect(key).toMatch(/^[A-Za-z0-9._:-]{8,100}$/);
    expect(generateKey()).not.toBe(key);
  });

  it('fingerprints carts independent of order', () => {
    expect(cartFingerprint(cart)).toBe(cartFingerprint([...cart].reverse()));
  });
});
