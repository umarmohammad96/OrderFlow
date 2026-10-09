import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as cartApi from '../api/cart.js';
import { useAuth } from './AuthContext.jsx';

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { isAuthenticated } = useAuth();
  const [cart, setCart] = useState(null);

  const refresh = useCallback(async () => {
    if (!isAuthenticated) {
      setCart(null);
      return null;
    }
    try {
      const c = await cartApi.getCart();
      setCart(c);
      return c;
    } catch {
      return null;
    }
  }, [isAuthenticated]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value = useMemo(
    () => ({
      cart,
      count: cart?.totalQuantity || 0,
      setCart,
      refresh,
      addItem: async (productId, quantity) => {
        const c = await cartApi.addItem(productId, quantity);
        setCart(c);
        return c;
      },
    }),
    [cart, refresh],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
