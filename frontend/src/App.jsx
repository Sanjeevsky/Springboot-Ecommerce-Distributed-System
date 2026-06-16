import React, { Suspense, useState } from "react";
import { Routes, Route, Outlet, Navigate, useLocation, useNavigate } from "react-router-dom";
import { isLoggedIn, isAdmin } from "./lib/auth.js";
import { Header } from "./components/storefront/Header.jsx";
import { Footer } from "./components/storefront/Footer.jsx";
import { CartDrawer } from "./components/storefront/CartDrawer.jsx";
import { Toast, Button } from "./components/index.js";
import { CheckCircle2, AlertTriangle } from "lucide-react";
import { useStore } from "./store/StoreContext.jsx";

import Home from "./pages/Home.jsx";
import Listing from "./pages/Listing.jsx";
import Product from "./pages/Product.jsx";
import Checkout from "./pages/Checkout.jsx";
import OrderConfirmation from "./pages/OrderConfirmation.jsx";
import Login from "./pages/Login.jsx";
import Signup from "./pages/Signup.jsx";

import AccountLayout from "./pages/account/AccountLayout.jsx";
import Orders from "./pages/account/Orders.jsx";
import Wishlist from "./pages/account/Wishlist.jsx";
import Addresses from "./pages/account/Addresses.jsx";
import Notifications from "./pages/account/Notifications.jsx";
import Payments from "./pages/account/Payments.jsx";
import Settings from "./pages/account/Settings.jsx";
import StudioLayout from "./pages/studio/StudioLayout.jsx";
import StudioProducts from "./pages/studio/StudioProducts.jsx";
import StudioProductEditor from "./pages/studio/StudioProductEditor.jsx";
import StudioInventory from "./pages/studio/StudioInventory.jsx";
import StudioCoupons from "./pages/studio/StudioCoupons.jsx";
import StudioActivity from "./pages/studio/StudioActivity.jsx";

const StudioHome = React.lazy(() => import("./pages/studio/StudioHome.jsx"));

// Redirects logged-out visitors to /login, remembering where they wanted to go.
function RequireAuth({ children }) {
  const location = useLocation();
  if (!isLoggedIn()) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return children;
}

// Admin-only routes: logged-out users go to login, customers go home.
function RequireAdmin({ children }) {
  const location = useLocation();
  if (!isLoggedIn()) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (!isAdmin()) {
    return <Navigate to="/" replace />;
  }
  return children;
}

// Listens for the global 401 signal from the API client and bounces the user to
// /login (remembering where they were) with an explanatory toast. Lives at the app
// root so it fires for storefront, account, and studio routes alike.
function AuthWatcher() {
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useStore();
  React.useEffect(() => {
    const onUnauthorized = (e) => {
      if (location.pathname === "/login") return;
      showToast({
        tone: "warning",
        title: "Please sign in",
        message: "Your session has expired or you don't have access. Sign in to continue.",
      });
      navigate("/login", { state: { from: e.detail?.from }, replace: true });
    };
    window.addEventListener("trove:unauthorized", onUnauthorized);
    return () => window.removeEventListener("trove:unauthorized", onUnauthorized);
  }, [navigate, location.pathname, showToast]);
  return null;
}

// Storefront chrome wrapper (header, footer, cart drawer, toast).
function StorefrontLayout() {
  const [cartOpen, setCartOpen] = useState(false);
  const { toast, dismissToast } = useStore();
  const location = useLocation();
  const bareFooter = location.pathname.startsWith("/checkout") || location.pathname.startsWith("/order/");

  // expose cart opener to pages via context-free window event
  React.useEffect(() => {
    const open = () => setCartOpen(true);
    window.addEventListener("trove:open-cart", open);
    return () => window.removeEventListener("trove:open-cart", open);
  }, []);

  return (
    <>
      <Header onOpenCart={() => setCartOpen(true)} />
      <main style={{ flex: 1 }}>
        <Outlet />
      </main>
      {!bareFooter && <Footer />}
      <CartDrawer open={cartOpen} onClose={() => setCartOpen(false)} />

      <div style={{ position: "fixed", top: 90, right: 20, zIndex: 1100, display: "flex", flexDirection: "column", gap: 10 }}>
        {toast && (
          <Toast {...toast}
            icon={toast.tone === "success" ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
            onClose={dismissToast}
            action={toast.tone === "success"
              ? <Button variant="link" size="sm" onClick={() => { dismissToast(); window.dispatchEvent(new Event("trove:open-cart")); }}>View cart</Button>
              : null} />
        )}
      </div>
    </>
  );
}

export default function App() {
  return (
    <>
    <AuthWatcher />
    <Routes>
      <Route element={<StorefrontLayout />}>
        <Route index element={<Home />} />
        <Route path="/c/:categoryId" element={<Listing />} />
        <Route path="/search" element={<Listing />} />
        <Route path="/p/:productId" element={<Product />} />
        <Route path="/checkout" element={<Checkout />} />
        <Route path="/order/:orderId" element={<OrderConfirmation />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<Signup />} />

        <Route path="/account" element={<RequireAuth><AccountLayout /></RequireAuth>}>
          <Route index element={<Navigate to="orders" replace />} />
          <Route path="orders" element={<Orders />} />
          <Route path="wishlist" element={<Wishlist />} />
          <Route path="addresses" element={<Addresses />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="payments" element={<Payments />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Route>

      <Route path="/studio" element={<RequireAdmin><StudioLayout /></RequireAdmin>}>
        <Route index element={(
          <Suspense fallback={<div className="studio-chart-empty">Loading analytics...</div>}>
            <StudioHome />
          </Suspense>
        )} />
        <Route path="products" element={<StudioProducts />} />
        <Route path="products/new" element={<StudioProductEditor />} />
        <Route path="products/:productId" element={<StudioProductEditor />} />
        <Route path="inventory" element={<StudioInventory />} />
        <Route path="coupons" element={<StudioCoupons />} />
        <Route path="activity" element={<StudioActivity />} />
      </Route>
    </Routes>
    </>
  );
}
