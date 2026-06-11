// Trove — domain service layer.
// Adapts the Spring Boot backend's response shapes to the frontend's data model.
// When VITE_USE_MOCKS=true, returns built-in mock data instead of calling the API.

import { api } from "./api.js";
import * as mock from "./mock.js";

const USE_MOCKS = String(import.meta.env.VITE_USE_MOCKS) === "true";

async function withFallback(fn, fallback) {
  if (USE_MOCKS) return fallback;
  return fn();
}

// Unwrap Spring ApiResponse<T> { success, data } envelope.
// Cart service returns data directly — pass those through unchanged.
function unwrap(res) {
  if (res !== null && typeof res === "object" && "success" in res) {
    if (!res.success) throw new Error(res.message || "API error");
    return res.data;
  }
  return res;
}

// ── Image pool for backend products that use placeholder images ───────────────
const UNSPLASH_POOL = [
  "1511707171634-5f897ff02aa9", "1505740420928-5e560c06d30e",
  "1524805444758-089113d48a6d", "1583394838336-acd977736f90",
  "1587829741301-dc798b83add3", "1544244015-0df4b3ffc6b0",
  "1567538096630-e0c55bd6374c", "1507473885765-e6ed057f782c",
];
const IMG = (id) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=600&q=72`;
let _imgIdx = 0;
const nextImg = () => IMG(UNSPLASH_POOL[_imgIdx++ % UNSPLASH_POOL.length]);
const isPlaceholder = (url) => !url || url.includes("https://x/") || url.length < 10;

// ── Product normalizer ────────────────────────────────────────────────────────
function normalizeProduct(p) {
  if (!p || typeof p !== "object") return p;
  const price = p.salePrice ?? p.price ?? 0;
  const mrp = p.mrpPrice ?? p.compareAt;
  const hasDiscount = mrp && mrp > price;
  const rawImg = (p.images && p.images[0]) || p.image || "";
  return {
    id: p.id,
    name: p.name || p.title || "Product",
    title: p.name || p.title || "Product",
    brand: typeof p.brand === "object" ? (p.brand?.name ?? "") : (p.brand ?? ""),
    brandId: typeof p.brand === "object" ? p.brand?.id : p.brandId,
    price,
    salePrice: price,
    mrpPrice: mrp ?? price,
    compareAt: hasDiscount ? mrp : undefined,
    cat: typeof p.category === "object"
      ? (p.category?.id ?? p.category?.categoryName ?? "general")
      : (p.category || p.cat || "general"),
    categoryId: typeof p.category === "object" ? p.category?.id : p.categoryId,
    catLabel: typeof p.category === "object"
      ? (p.category?.categoryName ?? "")
      : "",
    subCategoryId: typeof p.subCategory === "object" ? p.subCategory?.id : p.subCategoryId,
    subCategoryLabel: typeof p.subCategory === "object" ? p.subCategory?.subcategoryName : "",
    image: isPlaceholder(rawImg) ? nextImg() : rawImg,
    images: Array.isArray(p.images) ? p.images : (rawImg ? [rawImg] : []),
    rating: p.rating ?? 4.3,
    reviews: p.reviews ?? 0,
    stock: p.stock ?? 10,
    freeShipping: p.freeShipping ?? (price >= 50),
    badge: p.badge ?? (hasDiscount
      ? { tone: "sale", label: `−${Math.round((1 - price / mrp) * 100)}%` }
      : undefined),
    description: p.description ?? "",
    model: p.model ?? "",
    gstValue: p.gstValue ?? 0,
    discount: p.discount ?? 0,
    status: p.status ?? 1,
    active: (p.status ?? 1) === 1,
    hasVariant: p.hasVariant ?? false,
    modifiedAt: p.modifiedAt,
    variants: Array.isArray(p.variants) ? p.variants : [],
  };
}

function normalizeProducts(raw) {
  const list = raw?.content ?? (Array.isArray(raw) ? raw : []);
  return list.map(normalizeProduct);
}

// ── Category icon guesser ─────────────────────────────────────────────────────
function guessIcon(name = "") {
  const n = name.toLowerCase();
  if (n.match(/smartphone|phone|mobile/)) return "Smartphone";
  if (n.match(/laptop|macbook|notebook|computer/)) return "Laptop";
  if (n.match(/headphone|audio|speaker|earphone|earbuds/)) return "Headphones";
  if (n.match(/watch|wearable/)) return "Watch";
  if (n.match(/sneaker|shoe|footwear|running/)) return "Footprints";
  if (n.match(/cloth|wear|apparel|fashion|jeans|shirt/)) return "Shirt";
  if (n.match(/furni|sofa|chair|table|shelf|home/)) return "Lamp";
  if (n.match(/kitchen|cook|air fry|blender|appliance/)) return "UtensilsCrossed";
  if (n.match(/fitness|gym|sport|yoga|dumbbell|cycling/)) return "Dumbbell";
  if (n.match(/skin|beauty|serum|cosmet|fragrance|health/)) return "Sparkles";
  if (n.match(/electr|device|gadget/)) return "Zap";
  if (n.match(/game|gaming|console/)) return "Gamepad2";
  if (n.match(/outdoor|camp|trail/)) return "Tent";
  return "ShoppingBag";
}

// ── Order normalizer ──────────────────────────────────────────────────────────
const ORDER_STATUS_MAP = {
  PENDING:     { label: "Processing",       tone: "info" },
  CONFIRMED:   { label: "Confirmed",        tone: "success" },
  SHIPPED:     { label: "Out for delivery", tone: "info" },
  DELIVERED:   { label: "Delivered",        tone: "success" },
  CANCELLED:   { label: "Cancelled",        tone: "danger" },
  FAILED:      { label: "Failed",           tone: "danger" },
  COMPENSATED: { label: "Refunded",         tone: "neutral" },
  COMPLETED:   { label: "Delivered",        tone: "success" },
};

function normalizeOrder(o) {
  const s = ORDER_STATUS_MAP[o.status] ?? { label: o.status, tone: "neutral" };
  const etaMap = { info: "Processing your order", success: "Delivered", danger: "Refunded", neutral: "See details" };
  return {
    id: o.id,
    date: o.createdAt
      ? new Date(o.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })
      : "",
    status: s.label,
    statusTone: s.tone,
    total: o.orderTotal ?? o.total ?? 0,
    eta: etaMap[s.tone] ?? "See details",
    items: (o.orderItems || o.items || []).map((it) => ({
      id: it.id || it.productId,
      productId: it.productId,
      brand: it.brand ?? "",
      title: it.productName || it.title || "Item",
      price: it.unitPrice ?? it.price ?? 0,
      qty: it.qty ?? it.quantity ?? 1,
      image: isPlaceholder(it.image) ? IMG(UNSPLASH_POOL[0]) : (it.image ?? IMG(UNSPLASH_POOL[0])),
    })),
  };
}

// ── Notification normalizer ───────────────────────────────────────────────────
const NOTIF_META = {
  ORDER_PLACED:       { icon: "Package",      tone: "success" },
  ORDER_CONFIRMED:    { icon: "PackageCheck", tone: "success" },
  ORDER_SHIPPED:      { icon: "Truck",        tone: "info" },
  ORDER_DELIVERED:    { icon: "PackageCheck", tone: "success" },
  ORDER_CANCELLED:    { icon: "XCircle",      tone: "danger" },
  PAYMENT_INITIATED:  { icon: "CreditCard",   tone: "info" },
  PAYMENT_SUCCESS:    { icon: "ShieldCheck",  tone: "success" },
  PAYMENT_FAILED:     { icon: "AlertCircle",  tone: "danger" },
  STOCK_RESERVED:     { icon: "Package",      tone: "info" },
  STOCK_INSUFFICIENT: { icon: "AlertCircle",  tone: "danger" },
  REVIEW_APPROVED:    { icon: "Star",         tone: "accent" },
};

function normalizeNotification(n) {
  const meta = NOTIF_META[n.type] ?? { icon: "Bell", tone: "info" };
  let elapsed = "";
  if (n.createdAt) {
    const diff = Date.now() - new Date(n.createdAt).getTime();
    const m = Math.floor(diff / 60000);
    if (m < 60) elapsed = `${m}m ago`;
    else if (m < 1440) elapsed = `${Math.floor(m / 60)}h ago`;
    else elapsed = `${Math.floor(m / 1440)}d ago`;
  }
  return {
    id: n.id,
    icon: meta.icon,
    tone: meta.tone,
    title: n.subject || n.type || "Notification",
    body: n.message || "",
    time: elapsed,
    unread: !n.read,
  };
}

// ── Address normalizer ────────────────────────────────────────────────────────
function normalizeAddress(a, idx) {
  return {
    id: a.id,
    label: idx === 0 ? "Home" : "Work",
    name: a.user || "",
    line1: [a.home, a.streetLocality].filter(Boolean).join(", "),
    line2: a.landmark || "",
    city: a.city || "",
    state: a.state || "",
    zip: String(a.zipCode || ""),
    phone: a.phone || "",
    default: idx === 0,
  };
}

/* =========================================================================
   Catalog
   ========================================================================= */
export const catalog = {
  list: (params = {}) => {
    const qs = new URLSearchParams(params).toString();
    return withFallback(async () => {
      const res = await api.get(`/catalog-service/product/list${qs ? "?" + qs : ""}`);
      return normalizeProducts(unwrap(res));
    }, mock.products);
  },

  get: (id) =>
    withFallback(async () => {
      const res = await api.get(`/catalog-service/product/getProduct/${id}`);
      return normalizeProduct(unwrap(res));
    }, mock.products.find((p) => p.id === id) || mock.products[0]),

  search: ({ q = "", categoryId, brandId, size = 50, page = 0 } = {}) => {
    const params = new URLSearchParams({ size, page });
    if (q) params.set("q", q);
    if (categoryId) params.set("categoryId", categoryId);
    if (brandId) params.set("brandId", brandId);
    return withFallback(async () => {
      const res = await api.get(`/catalog-service/product/search?${params}`);
      return normalizeProducts(unwrap(res));
    }, mock.products.filter((p) =>
      (!q || (p.title + p.brand).toLowerCase().includes(q.toLowerCase())) &&
      (!categoryId || p.cat === categoryId)
    ));
  },

  suggest: (q, size = 5) => {
    if (!q || q.trim().length < 2) return Promise.resolve([]);
    return withFallback(async () => {
      const res = await api.get(`/catalog-service/product/suggest?q=${encodeURIComponent(q.trim())}&size=${size}`);
      const list = unwrap(res);
      return Array.isArray(list) ? list : [];
    }, []);
  },

  categories: () =>
    withFallback(async () => {
      const res = await api.get("/catalog-service/getCategories");
      const cats = unwrap(res);
      if (!Array.isArray(cats)) return mock.categories;
      return cats.map((c) => ({ id: c.id, label: c.categoryName, icon: guessIcon(c.categoryName) }));
    }, mock.categories),

  brands: () =>
    withFallback(async () => {
      const res = await api.get("/catalog-service/getBrands");
      const brands = unwrap(res);
      return Array.isArray(brands) ? brands.map((b) => (typeof b === "string" ? b : b.name)) : [];
    }, [...new Set(mock.products.map((p) => p.brand))]),

  categoryOptions: () =>
    withFallback(async () => {
      const res = await api.get("/catalog-service/getCategories");
      const cats = unwrap(res);
      return Array.isArray(cats) ? cats.map((c) => ({
        id: c.id,
        label: c.categoryName,
        subCategories: (c.subCategories || []).map((s) => ({ id: s.id, label: s.subcategoryName })),
      })) : [];
    }, mock.categories.map((c) => ({ ...c, subCategories: [] }))),

  brandOptions: () =>
    withFallback(async () => {
      const res = await api.get("/catalog-service/getBrands");
      const brands = unwrap(res);
      return Array.isArray(brands)
        ? brands.map((b) => (typeof b === "string" ? { id: b, label: b } : { id: b.id, label: b.name }))
        : [];
    }, [...new Set(mock.products.map((p) => p.brand))].map((name) => ({ id: name, label: name }))),

  adminList: ({ q = "", status, page = 0, size = 50, sort = "modifiedAt" } = {}) =>
    withFallback(async () => {
      const params = new URLSearchParams({ q, page, size, sort });
      if (status !== undefined && status !== null && status !== "") params.set("status", status);
      const res = await api.get(`/catalog-service/product/admin/list?${params}`);
      const data = unwrap(res) || {};
      return {
        items: normalizeProducts(data),
        page: data.number ?? page,
        totalPages: data.totalPages ?? 1,
        totalElements: data.totalElements ?? 0,
      };
    }, { items: mock.products.map(normalizeProduct), page: 0, totalPages: 1, totalElements: mock.products.length }),

  create: ({ brandId, categoryId, subCategoryId, ...payload }) =>
    withFallback(async () => {
      const params = new URLSearchParams({ brandId, categoryId, subCategoryId });
      const res = await api.post(`/catalog-service/product/addProduct?${params}`, payload);
      return normalizeProduct(unwrap(res));
    }, normalizeProduct({ id: `mock-${Date.now()}`, brandId, categoryId, subCategoryId, ...payload })),

  update: (id, payload) =>
    withFallback(async () => {
      const res = await api.put(`/catalog-service/product/${id}`, payload);
      return normalizeProduct(unwrap(res));
    }, normalizeProduct({ id, ...payload })),

  retire: (id) =>
    withFallback(async () => {
      const res = await api.del(`/catalog-service/product/${id}`);
      return normalizeProduct(unwrap(res));
    }, { id, status: 0 }),

  addVariant: (productId, payload) =>
    withFallback(async () => unwrap(await api.post(`/catalog-service/variant/add/${productId}`, payload)), {
      id: `mock-variant-${Date.now()}`,
      ...payload,
    }),

  updateVariant: (variantId, payload) =>
    withFallback(async () => unwrap(await api.put(`/catalog-service/variant/${variantId}`, payload)), {
      id: variantId,
      ...payload,
    }),

  deleteVariant: (variantId) =>
    withFallback(() => api.del(`/catalog-service/variant/${variantId}`), { id: variantId }),
};

/* =========================================================================
   Inventory
   ========================================================================= */
export const inventory = {
  available: (productId) =>
    withFallback(async () => {
      const res = await api.get(`/inventory-service/stock/${productId}/available`);
      const qty = unwrap(res);
      return { available: Number(qty) > 0, quantity: Number(qty) };
    }, { available: true, quantity: 12 }),

  list: () =>
    withFallback(async () => {
      const res = await api.get("/inventory-service/stock");
      const list = unwrap(res);
      return Array.isArray(list) ? list : [];
    }, []),

  set: (productId, variantId, totalQty) => {
    const suffix = variantId ? `/variant/${variantId}` : "";
    return withFallback(async () => unwrap(await api.put(
      `/inventory-service/stock/${productId}${suffix}`,
      { totalQty: Number(totalQty) }
    )), { productId, variantId, totalQty: Number(totalQty), reservedQty: 0, availableQty: Number(totalQty) });
  },
};

/* =========================================================================
   Cart  (cart-service returns data directly, no ApiResponse wrapper)
   ========================================================================= */
export const cart = {
  get: () =>
    withFallback(async () => {
      const res = await api.get("/cart-service/cart");
      return unwrap(res) ?? { items: [] };
    }, { items: [] }),

  // Backend expects { productId, qty } — NOT { productId, quantity }
  add: (productId, qty = 1) =>
    withFallback(() => api.post("/cart-service/cart/add", { productId, qty }), { ok: true }),

  updateQty: (productId, qty) =>
    withFallback(() => api.put(`/cart-service/cart/item/${productId}?qty=${qty}`, {}), { ok: true }),

  removeItem: (productId) =>
    withFallback(() => api.del(`/cart-service/cart/item/${productId}`), { ok: true }),

  clear: () =>
    withFallback(() => api.del("/cart-service/cart/clear"), { ok: true }),

  checkout: () => withFallback(() => api.get("/cart-service/cart/checkout"), { ok: true }),
};

/* =========================================================================
   Orders
   ========================================================================= */
export const orders = {
  list: () =>
    withFallback(async () => {
      const res = await api.get("/order-service/orders");
      const list = unwrap(res);
      return Array.isArray(list) ? list.map(normalizeOrder) : mock.orders;
    }, mock.orders),

  get: (id) =>
    withFallback(async () => {
      const res = await api.get(`/order-service/order/${id}`);
      return normalizeOrder(unwrap(res));
    }, mock.orders.find((o) => o.id === id)),

  place: (payload) =>
    withFallback(async () => {
      const res = await api.post("/order-service/order/saga", payload);
      return unwrap(res);
    }, { id: "TRV-48214", status: "PLACED" }),
};

/* =========================================================================
   Studio analytics
   ========================================================================= */
export const analytics = {
  summary: ({ from, to } = {}) =>
    withFallback(async () => {
      const params = new URLSearchParams();
      if (from) params.set("from", from);
      if (to) params.set("to", to);
      return unwrap(await api.get(`/order-service/analytics/summary${params.size ? `?${params}` : ""}`));
    }, {
      from,
      to,
      revenue: 0,
      orderCount: 0,
      averageOrderValue: 0,
      statusBreakdown: {},
    }),

  daily: (days = 30) =>
    withFallback(async () => {
      const result = unwrap(await api.get(`/order-service/analytics/daily?days=${days}`));
      return Array.isArray(result) ? result : [];
    }, []),

  topProducts: (limit = 10) =>
    withFallback(async () => {
      const result = unwrap(await api.get(`/order-service/analytics/top-products?limit=${limit}`));
      return Array.isArray(result) ? result : [];
    }, []),
};

/* =========================================================================
   Studio audit log  (admin activity: catalog price/status + inventory stock)
   ========================================================================= */
const AUDIT_ENDPOINTS = {
  catalog: "/catalog-service/audit",
  inventory: "/inventory-service/audit",
};

export const audit = {
  // Returns audit rows for one source, newest first, each tagged with its source.
  list: ({ source = "catalog", entityId, page = 0, size = 50 } = {}) =>
    withFallback(async () => {
      const params = new URLSearchParams({ page, size });
      if (entityId) params.set("entityId", entityId);
      const res = unwrap(await api.get(`${AUDIT_ENDPOINTS[source]}?${params}`));
      const rows = Array.isArray(res?.content) ? res.content : (Array.isArray(res) ? res : []);
      return rows.map((r) => ({ ...r, source }));
    }, []),
};

/* =========================================================================
   Media  (admin product image upload to object storage)
   ========================================================================= */
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    // readAsDataURL yields "data:<type>;base64,<payload>" — keep just the payload.
    reader.onload = () => resolve(String(reader.result).split(",")[1] || "");
    reader.onerror = () => reject(new Error("Could not read file"));
    reader.readAsDataURL(file);
  });
}

export const media = {
  // Uploads an image File and returns its public URL.
  upload: (file) =>
    withFallback(async () => {
      const dataBase64 = await fileToBase64(file);
      const res = unwrap(await api.post("/catalog-service/media/upload", {
        filename: file.name,
        contentType: file.type,
        dataBase64,
      }));
      return res?.url;
    }, "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=600&q=72"),
};

/* =========================================================================
   Payment
   ========================================================================= */
export const payment = {
  initiate: (orderId, method) =>
    withFallback(async () => {
      const res = await api.post("/payment-service/initiate", { orderId, method });
      return unwrap(res);
    }, { status: "AUTHORIZED" }),

  status: (orderId) =>
    withFallback(async () => {
      const res = await api.get(`/payment-service/status/${orderId}`);
      return unwrap(res);
    }, { status: "AUTHORIZED" }),
};

/* =========================================================================
   Coupons
   ========================================================================= */
export const coupons = {
  adminList: () =>
    withFallback(async () => {
      const res = await api.get("/coupon-service/admin/coupons");
      const list = unwrap(res);
      return Array.isArray(list) ? list : [];
    }, []),

  create: (payload) =>
    withFallback(async () => unwrap(await api.post("/coupon-service/coupon", payload)), {
      id: `mock-coupon-${Date.now()}`,
      active: true,
      usedCount: 0,
      ...payload,
    }),

  setActive: (couponId, active) =>
    withFallback(async () => unwrap(await api.put(
      `/coupon-service/coupon/${couponId}/active?active=${active}`,
      {}
    )), { id: couponId, active }),

  validate: (code, amount) => {
    const qs = `code=${encodeURIComponent(code)}${amount != null ? `&amount=${amount}` : ""}`;
    return withFallback(async () => {
      const res = await api.get(`/coupon-service/coupon/validate?${qs}`);
      const d = unwrap(res);
      return { valid: !!d?.valid, code: d?.couponCode ?? code, amount: d?.discountAmount ?? 0, type: "FLAT" };
    }, code?.toUpperCase() === "TROVE20"
      ? { valid: true, code: "TROVE20", amount: 20, type: "FLAT" }
      : { valid: false });
  },

  apply: (code, cartId) =>
    withFallback(() => api.post("/coupon-service/coupon/apply", { code, cartId }), { ok: true }),
};

/* =========================================================================
   Wishlist
   ========================================================================= */
export const wishlist = {
  get: () =>
    withFallback(async () => {
      const res = await api.get("/wishlist-service/wishlist");
      const list = unwrap(res);
      if (!Array.isArray(list) || list.length === 0) return [];
      // Enrich with full product data from catalog in parallel
      const products = await Promise.all(
        list.map((item) =>
          api.get(`/catalog-service/product/getProduct/${item.productId}`)
            .then((r) => normalizeProduct(unwrap(r)))
            .catch(() => normalizeProduct({
              id: item.productId,
              name: item.productName,
              salePrice: item.salePrice,
            }))
        )
      );
      return products;
    }, []),

  toggle: (productId, productName, salePrice) =>
    withFallback(async () => {
      const res = await api.post("/wishlist-service/wishlist", { productId, productName, salePrice });
      return unwrap(res);
    }, { ok: true }),

  remove: (productId) =>
    withFallback(async () => {
      const res = await api.del(`/wishlist-service/wishlist/${productId}`);
      return unwrap(res);
    }, { ok: true }),

  moveToCart: (productId) =>
    withFallback(async () => {
      const res = await api.post(`/wishlist-service/wishlist/${productId}/move-to-cart`, {});
      return unwrap(res);
    }, { ok: true }),
};

/* =========================================================================
   Customer / Addresses
   ========================================================================= */
export const customer = {
  addresses: () =>
    withFallback(async () => {
      const res = await api.get("/customer-service/addresses");
      const list = unwrap(res);
      return Array.isArray(list) ? list.map(normalizeAddress) : mock.addresses;
    }, mock.addresses),

  addAddress: (addr) =>
    withFallback(async () => {
      const res = await api.post("/customer-service/address", addr);
      return unwrap(res);
    }, { ok: true }),

  updateAddress: (id, addr) =>
    withFallback(async () => {
      const res = await api.put(`/customer-service/address/${id}`, addr);
      return unwrap(res);
    }, { ok: true }),

  deleteAddress: (id) =>
    withFallback(() => api.del(`/customer-service/address/${id}`), { ok: true }),
};

/* =========================================================================
   Notifications  (returns array directly, no wrapper)
   ========================================================================= */
export const notifications = {
  list: () =>
    withFallback(async () => {
      const res = await api.get("/notification-service/notifications");
      const list = Array.isArray(res) ? res : unwrap(res);
      return Array.isArray(list) ? list.map(normalizeNotification) : mock.notifications;
    }, mock.notifications),

  unread: () =>
    withFallback(async () => {
      const res = await api.get("/notification-service/notifications/unread");
      const list = Array.isArray(res) ? res : unwrap(res);
      return Array.isArray(list) ? list.map(normalizeNotification) : mock.notifications.filter((n) => n.unread);
    }, mock.notifications.filter((n) => n.unread)),

  markRead: (id) =>
    withFallback(() => api.put(`/notification-service/notifications/${id}/read`, {}), { ok: true }),
};

/* =========================================================================
   Reviews
   ========================================================================= */
export const reviews = {
  forProduct: (productId) =>
    withFallback(async () => {
      const res = await api.get(`/review-service/review/product/${productId}`);
      const list = Array.isArray(res) ? res : unwrap(res);
      return Array.isArray(list) ? list : [];
    }, []),

  summary: (productId) =>
    withFallback(async () => {
      const res = await api.get(`/review-service/review/product/${productId}/summary`);
      const d = unwrap(res);
      return { average: d?.averageRating ?? 0, count: d?.totalReviews ?? 0 };
    }, { average: 4.5, count: 1280 }),

  create: (payload) =>
    withFallback(async () => {
      const res = await api.post("/review-service/review", payload);
      return unwrap(res);
    }, { ok: true }),
};

/* =========================================================================
   Auth
   ========================================================================= */
export const auth = {
  login: (email, password) =>
    withFallback(async () => {
      const res = await api.post("/auth-service/login", { email, password });
      const d = unwrap(res);
      return { token: d?.token, user: { email, name: email.split("@")[0], role: d?.role || "CUSTOMER" } };
    }, { token: "mock-jwt", user: mock.user }),

  updatePassword: (email, oldPassword, newPassword) =>
    withFallback(async () => {
      const res = await api.put("/auth-service/updatePassword", { email, oldPassword, newPassword });
      return unwrap(res);
    }, { ok: true }),

  signup: async (payload) => {
    if (USE_MOCKS) return { token: "mock-jwt", user: mock.user };
    await api.post("/auth-service/signup", payload);
    const loginRes = await api.post("/auth-service/login", {
      email: payload.email,
      password: payload.password,
    });
    const d = unwrap(loginRes);
    return { token: d?.token, user: { email: payload.email, name: payload.name, role: d?.role || "CUSTOMER" } };
  },
};
