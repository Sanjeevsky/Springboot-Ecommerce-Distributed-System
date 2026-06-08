import React, { useState, useEffect } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { Package, Heart, MapPin, Bell, CreditCard, Settings } from "lucide-react";
import { Avatar, Badge } from "../../components/index.js";
import { useStore } from "../../store/StoreContext.jsx";
import { Logo } from "../../components/storefront/Logo.jsx";
import { notifications as notificationsApi } from "../../lib/services.js";

const NAV = [
  { to: "orders",        label: "Orders",           Icon: Package },
  { to: "wishlist",      label: "Saved items",       Icon: Heart },
  { to: "addresses",     label: "Addresses",         Icon: MapPin },
  { to: "notifications", label: "Notifications",     Icon: Bell },
  { to: "payments",      label: "Payment methods",   Icon: CreditCard },
  { to: "settings",      label: "Settings",          Icon: Settings },
];

function loadUser() {
  try { return JSON.parse(localStorage.getItem("trove_user") || "{}"); } catch { return {}; }
}

export default function AccountLayout() {
  const navigate = useNavigate();
  const storedUser = loadUser();
  const user = {
    name: storedUser.name || storedUser.email?.split("@")[0] || "Account",
    email: storedUser.email || "",
    plus: true,
  };
  const [unread, setUnread] = useState(0);
  useEffect(() => {
    notificationsApi.unread().then((list) => setUnread(list.length)).catch(() => {});
  }, []);

  return (
    <div style={{ minHeight: "100vh", background: "var(--bg)" }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "32px 24px 64px", display: "grid", gridTemplateColumns: "248px 1fr", gap: 36, alignItems: "start" }}>
        <aside style={{ position: "sticky", top: 138 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, padding: 16, border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", marginBottom: 16 }}>
            <Avatar name={user.name} size="md" />
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: "var(--text)" }}>{user.name}</div>
              {user.plus && <Badge tone="primary" size="sm" style={{ marginTop: 3 }}>Trove Plus</Badge>}
            </div>
          </div>

          <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
            {NAV.map(({ to, label, Icon }) => (
              <NavLink key={to} to={to}
                style={({ isActive }) => ({
                  display: "flex", alignItems: "center", gap: 12, padding: "11px 14px",
                  border: "none", borderRadius: "var(--radius-md)", cursor: "pointer", textAlign: "left",
                  textDecoration: "none",
                  background: isActive ? "var(--primary-subtle)" : "transparent",
                  color: isActive ? "var(--primary)" : "var(--text-secondary)",
                  fontFamily: "var(--font-text)", fontSize: 14.5, fontWeight: isActive ? 700 : 500,
                  transition: "background var(--dur-fast)",
                })}>
                <Icon size={18} />
                <span style={{ flex: 1 }}>{label}</span>
                {to === "notifications" && unread > 0 && (
                  <Badge tone="sale" variant="solid" size="sm">{unread}</Badge>
                )}
              </NavLink>
            ))}
          </nav>
        </aside>

        <main style={{ minWidth: 0 }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
