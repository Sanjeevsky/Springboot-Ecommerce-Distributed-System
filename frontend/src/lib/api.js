// Trove — low-level API client for the Spring Cloud Gateway.
//
// Endpoint prefixes (discovered from the backend controllers; gateway :8081):
//   auth-service      POST /auth-service/signup, /auth-service/login
//   catalog-service   GET  /catalog-service/product/list, /product/getProduct/{id},
//                          /product/search?q=, /getCategories, /getBrands
//   cart-service      GET  /cart-service/cart, POST /cart-service/cart/add,
//                          GET /cart-service/cart/checkout
//   order-service     GET  /order-service/orders, /order-service/order/{id},
//                          POST /order-service/order/saga
//   payment-service   POST /payment-service/initiate, GET /payment-service/status/{orderId}
//   inventory-service GET  /inventory-service/stock/{productId}/available
//   wishlist-service  GET  /wishlist-service/wishlist, POST /wishlist-service/wishlist,
//                          POST /wishlist-service/wishlist/{productId}/move-to-cart
//   coupon-service    GET  /coupon-service/coupon/validate?code=, POST /coupon-service/coupon/apply,
//                          GET /coupon-service/coupons
//   customer-service  GET  /customer-service/addresses, POST /customer-service/address
//   notification-svc  GET  /notification-service/notifications, /notifications/unread
//   review-service    GET  /review-service/review/product/{id}, /summary, POST /review-service/review

import { logout } from "./auth.js";

const BASE = import.meta.env.VITE_API_BASE || "/api";

function authHeader() {
  const token = localStorage.getItem("trove_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// A 401 from the gateway means the session is missing/expired/invalid. Clear it and
// signal the app to bounce the user to /login with a toast. Login/signup are exempt —
// their own 401 (bad credentials) is surfaced by the form, not the global handler.
function handleUnauthorized(path) {
  if (path.startsWith("/auth-service/")) return;
  logout();
  window.dispatchEvent(new CustomEvent("trove:unauthorized", {
    detail: { from: window.location.pathname + window.location.search },
  }));
}

async function request(path, { method = "GET", body, headers } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...authHeader(),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    if (res.status === 401) handleUnauthorized(path);
    const text = await res.text().catch(() => "");
    const err = new Error(`${res.status} ${res.statusText} — ${path}`);
    err.status = res.status;
    err.body = text;
    throw err;
  }
  const ct = res.headers.get("content-type") || "";
  return ct.includes("application/json") ? res.json() : res.text();
}

export const api = {
  get: (p, o) => request(p, { ...o, method: "GET" }),
  post: (p, body, o) => request(p, { ...o, method: "POST", body }),
  put: (p, body, o) => request(p, { ...o, method: "PUT", body }),
  del: (p, o) => request(p, { ...o, method: "DELETE" }),
};
