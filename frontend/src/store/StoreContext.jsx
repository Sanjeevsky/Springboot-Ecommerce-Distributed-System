// Trove — cart + wishlist + toast state, shared via React context.
// Persists to localStorage so a refresh keeps your cart (handy in dev).
import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { cart as cartApi } from "../lib/services.js";

const StoreCtx = createContext(null);
export const useStore = () => useContext(StoreCtx);

const load = (k, fallback) => {
  try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : fallback; }
  catch { return fallback; }
};

export function StoreProvider({ children }) {
  const [cart, setCart] = useState(() => load("trove_cart", []));
  const [wishlist, setWishlist] = useState(() => load("trove_wishlist", { p3: true }));
  const [toast, setToast] = useState(null);

  useEffect(() => { localStorage.setItem("trove_cart", JSON.stringify(cart)); }, [cart]);
  useEffect(() => { localStorage.setItem("trove_wishlist", JSON.stringify(wishlist)); }, [wishlist]);

  const showToast = useCallback((cfg) => {
    setToast(cfg);
    clearTimeout(window.__trvToast);
    window.__trvToast = setTimeout(() => setToast(null), 3200);
  }, []);

  const addToCart = useCallback((product, qty = 1) => {
    setCart((c) => {
      const ex = c.find((x) => x.id === product.id);
      if (ex) return c.map((x) => (x.id === product.id ? { ...x, qty: x.qty + qty } : x));
      return [...c, { ...product, qty }];
    });
    if (localStorage.getItem("trove_token")) {
      cartApi.add(product.id, qty).catch(() => {});
    }
    showToast({ tone: "success", title: "Added to cart", message: `${product.title} · Qty ${qty}` });
  }, [showToast]);

  const setQty = useCallback((id, qty) => {
    setCart((c) => c.map((x) => (x.id === id ? { ...x, qty } : x)));
    if (localStorage.getItem("trove_token")) cartApi.updateQty(id, qty).catch(() => {});
  }, []);

  const removeItem = useCallback((id) => {
    setCart((c) => c.filter((x) => x.id !== id));
    if (localStorage.getItem("trove_token")) cartApi.removeItem(id).catch(() => {});
  }, []);
  const clearCart = useCallback(() => setCart([]), []);

  const toggleWish = useCallback((id) =>
    setWishlist((w) => ({ ...w, [id]: !w[id] })), []);

  const cartCount = cart.reduce((s, i) => s + i.qty, 0);
  const cartSubtotal = cart.reduce((s, i) => s + i.price * i.qty, 0);
  const wishCount = Object.values(wishlist).filter(Boolean).length;

  const value = {
    cart, cartCount, cartSubtotal,
    addToCart, setQty, removeItem, clearCart,
    wishlist, wishCount, toggleWish,
    toast, showToast, dismissToast: () => setToast(null),
  };
  return <StoreCtx.Provider value={value}>{children}</StoreCtx.Provider>;
}
