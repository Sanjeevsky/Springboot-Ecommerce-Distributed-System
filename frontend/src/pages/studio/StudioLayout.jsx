import React from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Activity, BarChart3, Boxes, LogOut, Package, ShoppingBag, TicketPercent } from "lucide-react";
import { Logo } from "../../components/storefront/Logo.jsx";
import { currentUser, logout } from "../../lib/auth.js";
import "./studio.css";

const NAV = [
  { to: "/studio", end: true, label: "Overview", Icon: BarChart3 },
  { to: "/studio/products", label: "Products", Icon: Package },
  { to: "/studio/inventory", label: "Inventory", Icon: Boxes },
  { to: "/studio/coupons", label: "Coupons", Icon: TicketPercent },
  { to: "/studio/activity", label: "Activity", Icon: Activity },
];

export default function StudioLayout() {
  const navigate = useNavigate();
  const user = currentUser();

  const signOut = () => {
    logout();
    navigate("/login");
  };

  return (
    <div className="studio-shell">
      <header className="studio-topbar">
        <div className="studio-brand">
          <Logo />
          <span className="studio-brand-divider" />
          <strong>Studio</strong>
        </div>
        <div className="studio-topbar-actions">
          <button className="studio-icon-command" onClick={() => navigate("/")} title="Open storefront" aria-label="Open storefront">
            <ShoppingBag size={18} />
          </button>
          <div className="studio-user">
            <span>{user.email}</span>
            <strong>Admin</strong>
          </div>
          <button className="studio-text-command" onClick={signOut} title="Sign out" aria-label="Sign out">
            <LogOut size={18} />
            <span>Sign out</span>
          </button>
        </div>
      </header>

      <div className="studio-workspace">
        <aside className="studio-sidebar">
          <nav aria-label="Studio navigation">
            {NAV.map(({ to, end, label, Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={end}
                className={({ isActive }) => `studio-nav-link${isActive ? " is-active" : ""}`}
              >
                <Icon size={18} />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>
        </aside>
        <main className="studio-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
