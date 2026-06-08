// Trove — built-in mock data. Used when VITE_USE_MOCKS=true or when an API
// call fails (so the UI is always demoable). Mirrors the backend's shapes
// loosely (catalog products, categories, orders, coupons, etc.).

const IMG = (id, w = 600) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&q=72`;

export const categories = [
  { id: "electronics", label: "Electronics", icon: "Smartphone" },
  { id: "home", label: "Home & Living", icon: "Lamp" },
  { id: "fashion", label: "Fashion", icon: "Shirt" },
  { id: "audio", label: "Audio", icon: "Headphones" },
  { id: "outdoors", label: "Outdoors", icon: "Tent" },
  { id: "gaming", label: "Gaming", icon: "Gamepad2" },
  { id: "beauty", label: "Beauty", icon: "Sparkles" },
  { id: "kids", label: "Kids", icon: "Baby" },
];

export const products = [
  { id: "p1", brand: "Aurex", title: "Wireless Noise-Cancelling Headphones", cat: "audio", price: 249, compareAt: 329, rating: 4.6, reviews: 1280, freeShipping: true, badge: { tone: "sale", label: "−24%" }, stock: 12, image: IMG("1505740420928-5e560c06d30e") },
  { id: "p2", brand: "Tempo", title: "Analog Field Watch, Sand", cat: "fashion", price: 189, rating: 4.4, reviews: 342, freeShipping: true, stock: 5, image: IMG("1524805444758-089113d48a6d") },
  { id: "p3", brand: "Loft", title: "Walnut Lounge Chair", cat: "home", price: 429, compareAt: 549, rating: 4.8, reviews: 96, freeShipping: true, badge: { tone: "accent", label: "Bestseller" }, stock: 3, image: IMG("1567538096630-e0c55bd6374c") },
  { id: "p4", brand: "Nimbus", title: "Smartphone 5G, 256 GB", cat: "electronics", price: 799, compareAt: 899, rating: 4.5, reviews: 2104, freeShipping: true, badge: { tone: "sale", label: "−11%" }, stock: 24, image: IMG("1511707171634-5f897ff02aa9") },
  { id: "p5", brand: "Field & Co", title: "Insulated Trail Bottle, 32 oz", cat: "outdoors", price: 34.99, rating: 4.7, reviews: 880, freeShipping: false, stock: 60, image: IMG("1602143407151-7111542de6e8") },
  { id: "p6", brand: "Pixel", title: "Mechanical Keyboard, Low-Profile", cat: "gaming", price: 129, compareAt: 159, rating: 4.6, reviews: 540, freeShipping: true, badge: { tone: "sale", label: "−19%" }, stock: 8, image: IMG("1587829741301-dc798b83add3") },
  { id: "p7", brand: "Vera", title: "Ceramic Pour-Over Set", cat: "home", price: 58, rating: 4.3, reviews: 211, freeShipping: false, stock: 18, image: IMG("1495474472287-4d71bcdd2085") },
  { id: "p8", brand: "Lumen", title: "Smart Desk Lamp, Warm", cat: "home", price: 89, compareAt: 109, rating: 4.5, reviews: 367, freeShipping: true, badge: { tone: "sale", label: "−18%" }, stock: 14, image: IMG("1507473885765-e6ed057f782c") },
  { id: "p9", brand: "Strato", title: "Running Sneakers, Cloud", cat: "fashion", price: 119, rating: 4.4, reviews: 1530, freeShipping: true, stock: 22, image: IMG("1542291026-7eec264c27ff") },
  { id: "p10", brand: "Aurex", title: "True-Wireless Earbuds, Pro", cat: "audio", price: 149, compareAt: 199, rating: 4.5, reviews: 3120, freeShipping: true, badge: { tone: "sale", label: "−25%" }, stock: 40, image: IMG("1590658268037-6bf12165a8df") },
  { id: "p11", brand: "Nimbus", title: 'Tablet 11", 128 GB', cat: "electronics", price: 459, rating: 4.6, reviews: 712, freeShipping: true, stock: 9, image: IMG("1544244015-0df4b3ffc6b0") },
  { id: "p12", brand: "Forma", title: "Linen Throw Blanket", cat: "home", price: 72, rating: 4.7, reviews: 158, freeShipping: false, stock: 30, image: IMG("1600369671236-e74521d4b6ad") },
];

export const orders = [
  { id: "TRV-48213", date: "Jun 3, 2026", status: "Out for delivery", statusTone: "info", total: 398.0, eta: "Arrives today", items: [{ ...products[0], qty: 1 }, { ...products[9], qty: 1 }] },
  { id: "TRV-47980", date: "May 28, 2026", status: "Delivered", statusTone: "success", total: 429.0, eta: "Delivered May 31", items: [{ ...products[2], qty: 1 }] },
  { id: "TRV-47551", date: "May 14, 2026", status: "Delivered", statusTone: "success", total: 218.99, eta: "Delivered May 17", items: [{ ...products[5], qty: 1 }, { ...products[4], qty: 1 }, { ...products[11], qty: 1 }] },
  { id: "TRV-46902", date: "Apr 30, 2026", status: "Cancelled", statusTone: "danger", total: 89.0, eta: "Refunded", items: [{ ...products[7], qty: 1 }] },
];

export const addresses = [
  { id: "a1", label: "Home", name: "Maya Okafor", line1: "2841 Mission Street", line2: "Apt 4B", city: "San Francisco", state: "CA", zip: "94110", phone: "(415) 555-0118", default: true },
  { id: "a2", label: "Work", name: "Maya Okafor", line1: "535 Market Street", line2: "Floor 12", city: "San Francisco", state: "CA", zip: "94105", phone: "(415) 555-0118", default: false },
];

export const notifications = [
  { id: "n1", icon: "Truck", tone: "info", title: "Your order is out for delivery", body: "Order #TRV-48213 will arrive today before 9pm.", time: "2h ago", unread: true },
  { id: "n2", icon: "Tag", tone: "sale", title: "Price drop on your saved item", body: "Walnut Lounge Chair dropped to $429.00 — 22% off.", time: "5h ago", unread: true },
  { id: "n3", icon: "Star", tone: "accent", title: "How was your order?", body: "Leave a review for the Aurex Earbuds and help other shoppers.", time: "1d ago", unread: false },
  { id: "n4", icon: "PackageCheck", tone: "success", title: "Delivered", body: "Order #TRV-47980 was delivered. Enjoy your Walnut Lounge Chair!", time: "6d ago", unread: false },
  { id: "n5", icon: "ShieldCheck", tone: "primary", title: "New sign-in to your account", body: "We noticed a sign-in from San Francisco, CA on a new device.", time: "1w ago", unread: false },
];

export const user = { name: "Maya Okafor", email: "maya.okafor@email.com", plus: true };
