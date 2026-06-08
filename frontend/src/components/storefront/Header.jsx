import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Search, Heart, ShoppingBag, User, Moon, Sun, LayoutGrid } from "lucide-react";
import { Input } from "../index.js";
import { Logo } from "./Logo.jsx";
import { useStore } from "../../store/StoreContext.jsx";
import { useTheme } from "../../store/ThemeContext.jsx";
import { catalog } from "../../lib/services.js";

function CountDot({ n }) {
  if (!n) return null;
  return (
    <span style={{ position: "absolute", top: -7, right: -9, minWidth: 17, height: 17, padding: "0 4px", borderRadius: 999, background: "var(--primary)", color: "var(--on-primary)", fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 700, lineHeight: "17px", display: "flex", alignItems: "center", justifyContent: "center", border: "2px solid var(--surface)" }}>{n}</span>
  );
}

const navBtn = { display: "flex", flexDirection: "column", alignItems: "center", gap: 3, border: "none", background: "transparent", cursor: "pointer", color: "var(--text-secondary)", padding: "6px 10px", borderRadius: "var(--radius-md)", fontFamily: "var(--font-text)" };
const navLabel = { fontSize: 11, fontWeight: 600 };

export function Header({ onOpenCart }) {
  const navigate = useNavigate();
  const { cartCount, wishCount } = useStore();
  const { theme, toggle } = useTheme();
  const [q, setQ] = useState("");
  const [categories, setCategories] = useState([]);

  useEffect(() => { catalog.categories().then(setCategories).catch(() => {}); }, []);

  const submit = (e) => { e.preventDefault(); navigate(`/search?q=${encodeURIComponent(q)}`); };

  return (
    <header style={{ position: "sticky", top: 0, zIndex: 100, background: "var(--surface)", borderBottom: "1px solid var(--border)" }}>
      <div style={{ background: "var(--ink-900)", color: "var(--paper)", textAlign: "center", padding: "7px 16px", fontSize: 13, fontWeight: 500 }}>
        Free shipping over $50 · Free 30-day returns · <span style={{ color: "var(--amber-300)" }}>Today's treasure: up to 25% off audio</span>
      </div>

      <div style={{ maxWidth: 1320, margin: "0 auto", padding: "0 24px", height: 72, display: "flex", alignItems: "center", gap: 22 }}>
        <Logo size={30} />
        <form onSubmit={submit} style={{ flex: 1, maxWidth: 560, marginLeft: 8 }}>
          <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search for anything…"
            iconLeft={<Search size={18} />} wrapStyle={{ background: "var(--surface-2)", border: "1px solid var(--border)" }} />
        </form>

        <div style={{ display: "flex", alignItems: "center", gap: 6, marginLeft: "auto" }}>
          <button onClick={toggle} aria-label="Toggle theme" style={{ ...navBtn, padding: 10 }}>
            {theme === "dark" ? <Sun size={18} /> : <Moon size={18} />}
          </button>
          <button onClick={() => navigate("/account/orders")} style={navBtn}>
            <User size={18} /><span style={navLabel}>Account</span>
          </button>
          <button onClick={() => navigate("/account/wishlist")} style={navBtn}>
            <span style={{ position: "relative", display: "inline-flex" }}><Heart size={18} /><CountDot n={wishCount} /></span>
            <span style={navLabel}>Saved</span>
          </button>
          <button onClick={onOpenCart} style={navBtn}>
            <span style={{ position: "relative", display: "inline-flex" }}><ShoppingBag size={18} /><CountDot n={cartCount} /></span>
            <span style={navLabel}>Cart</span>
          </button>
        </div>
      </div>

      <nav style={{ borderTop: "1px solid var(--border)" }}>
        <div style={{ maxWidth: 1320, margin: "0 auto", padding: "0 24px", height: 46, display: "flex", alignItems: "center", gap: 4, overflowX: "auto" }}>
          <Link to="/c/all" style={{ display: "inline-flex", alignItems: "center", gap: 6, whiteSpace: "nowrap", color: "var(--text)", fontWeight: 700, fontSize: 14, padding: "0 12px", height: 46, fontFamily: "var(--font-text)" }}>
            <LayoutGrid size={16} /> All categories
          </Link>
          {categories.map((c) => (
            <Link key={c.id} to={`/c/${c.id}`} style={{ display: "inline-flex", alignItems: "center", whiteSpace: "nowrap", color: "var(--text-secondary)", fontWeight: 500, fontSize: 14, padding: "0 12px", height: 46, fontFamily: "var(--font-text)" }}>
              {c.label}
            </Link>
          ))}
        </div>
      </nav>
    </header>
  );
}
