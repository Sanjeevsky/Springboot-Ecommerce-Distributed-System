import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, ShieldCheck, RotateCcw, BadgeCheck,
  Smartphone, Laptop, Headphones, Watch, Footprints, Shirt,
  Lamp, UtensilsCrossed, Dumbbell, Sparkles, Zap, ShoppingBag,
  Tent, Gamepad2, Baby } from "lucide-react";
import { Button } from "../components/index.js";
import { NavCard } from "../components/storefront/NavCard.jsx";
import { useStore } from "../store/StoreContext.jsx";
import { catalog } from "../lib/services.js";

const CAT_ICONS = {
  Smartphone, Laptop, Headphones, Watch, Footprints, Shirt,
  Lamp, UtensilsCrossed, Dumbbell, Sparkles, Zap, ShoppingBag,
  Tent, Gamepad2, Baby,
};

function SectionHead({ eyebrow, title, to, action }) {
  return (
    <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", marginBottom: 20, gap: 16, flexWrap: "wrap" }}>
      <div>
        {eyebrow && <div className="t-eyebrow" style={{ marginBottom: 6 }}>{eyebrow}</div>}
        <h2 style={{ fontFamily: "var(--font-display)", fontSize: 28, fontWeight: 700, letterSpacing: "-0.025em", color: "var(--text)" }}>{title}</h2>
      </div>
      {action && (
        <Link to={to} className="trv-seeall" style={{ color: "var(--primary)", fontFamily: "var(--font-text)", fontSize: 15, fontWeight: 600, display: "inline-flex", alignItems: "center", gap: 6 }}>
          {action} <ArrowRight size={16} />
        </Link>
      )}
    </div>
  );
}

function Hero() {
  const navigate = useNavigate();
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1.05fr 1fr", gap: 24, alignItems: "stretch" }}>
      <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", padding: "8px 8px 8px 0" }}>
        <div className="t-eyebrow" style={{ color: "var(--primary)", marginBottom: 14 }}>A world of things</div>
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: "clamp(40px, 5vw, 62px)", fontWeight: 800, letterSpacing: "-0.035em", lineHeight: 1.02, color: "var(--text)", maxWidth: 520 }}>
          Find what you love, from sellers you trust.
        </h1>
        <p style={{ fontSize: 18, color: "var(--text-secondary)", lineHeight: 1.5, marginTop: 18, maxWidth: 440 }}>
          Millions of items — electronics, home, fashion and more — in one calm, fast storefront. Free returns within 30 days.
        </p>
        <div style={{ display: "flex", gap: 12, marginTop: 28 }}>
          <Button variant="primary" size="lg" onClick={() => navigate("/c/all")} iconRight={<ArrowRight size={18} />}>Start shopping</Button>
          <Button variant="secondary" size="lg" onClick={() => navigate("/c/audio")}>Today's deals</Button>
        </div>
        <div style={{ display: "flex", gap: 22, marginTop: 30, flexWrap: "wrap" }}>
          {[["Protected payments", ShieldCheck], ["Free 30-day returns", RotateCcw], ["Verified sellers", BadgeCheck]].map(([t, Ic]) => (
            <span key={t} style={{ display: "inline-flex", alignItems: "center", gap: 7, color: "var(--text-muted)", fontSize: 13.5, fontWeight: 500 }}>
              <Ic size={16} style={{ color: "var(--primary)" }} /> {t}
            </span>
          ))}
        </div>
      </div>
      <div style={{ position: "relative", borderRadius: "var(--radius-2xl)", overflow: "hidden", minHeight: 420, background: "var(--surface-2)" }}>
        <img src="https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=900&q=72" alt="" style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover" }} />
        <div style={{ position: "absolute", left: 18, bottom: 18 }}>
          <div style={{ background: "var(--surface)", borderRadius: "var(--radius-lg)", padding: "12px 16px", boxShadow: "var(--shadow-lg)" }}>
            <div className="t-eyebrow" style={{ color: "var(--sale)" }}>Today's treasure</div>
            <div style={{ fontFamily: "var(--font-display)", fontSize: 19, fontWeight: 700, color: "var(--text)", marginTop: 2 }}>Up to 25% off audio</div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function Home() {
  const navigate = useNavigate();
  const { wishlist, toggleWish, addToCart } = useStore();
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);

  useEffect(() => { catalog.list({ size: 50 }).then(setProducts); }, []);
  useEffect(() => { catalog.categories().then(setCategories); }, []);

  const withHandlers = (p) => ({ ...p, wished: !!wishlist[p.id], onWish: () => toggleWish(p.id), onAdd: () => addToCart(p) });
  const deals = products.filter((p) => p.compareAt).slice(0, 5);
  const trending = products.slice(0, 10);

  return (
    <div style={{ maxWidth: 1320, margin: "0 auto", padding: "28px 24px 0" }}>
      <Hero />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(8, 1fr)", gap: 12, marginTop: 40 }}>
        {categories.map((c) => {
          const Ic = CAT_ICONS[c.icon];
          return (
            <Link key={c.id} to={`/c/${c.id}`} className="trv-cat-tile" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: "18px 8px", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", transition: "all 200ms var(--ease-out)" }}>
              <span style={{ width: 46, height: 46, borderRadius: 999, background: "var(--primary-subtle)", display: "inline-flex", alignItems: "center", justifyContent: "center", color: "var(--primary)" }}>
                {Ic && <Ic size={22} />}
              </span>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>{c.label}</span>
            </Link>
          );
        })}
      </div>

      <section style={{ marginTop: 56 }}>
        <SectionHead eyebrow="Today's treasure" title="Deals worth grabbing" to="/c/all" action="See all deals" />
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 16 }}>
          {deals.map((p) => <NavCard key={p.id} product={withHandlers(p)} />)}
        </div>
      </section>

      <section style={{ marginTop: 56, borderRadius: "var(--radius-2xl)", overflow: "hidden", background: "var(--primary)" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", alignItems: "center" }}>
          <div style={{ padding: "clamp(28px, 4vw, 52px)" }}>
            <div style={{ color: "var(--emerald-100)", fontSize: 12, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase" }}>Trove Plus</div>
            <h2 style={{ fontFamily: "var(--font-display)", fontSize: "clamp(26px, 3vw, 38px)", fontWeight: 800, letterSpacing: "-0.03em", color: "#fff", marginTop: 10, lineHeight: 1.05 }}>Free 2-day shipping, all year.</h2>
            <p style={{ color: "rgba(255,255,255,0.85)", fontSize: 16, marginTop: 12, maxWidth: 380, lineHeight: 1.5 }}>Join Trove Plus for free fast delivery, members-only deals, and extended returns.</p>
            <div style={{ marginTop: 22 }}><Button variant="accent" size="lg">Try Plus free for 30 days</Button></div>
          </div>
          <div style={{ position: "relative", minHeight: 240, height: "100%" }}>
            <img src="https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?auto=format&fit=crop&w=700&q=72" alt="" style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover" }} />
          </div>
        </div>
      </section>

      <section style={{ marginTop: 56 }}>
        <SectionHead eyebrow="Popular right now" title="Trending across Trove" to="/c/all" action="Browse all" />
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 16 }}>
          {trending.map((p) => <NavCard key={p.id} product={withHandlers(p)} />)}
        </div>
      </section>

      <style>{`
        .trv-cat-tile:hover{ border-color:var(--primary-border); box-shadow:var(--shadow-sm); transform:translateY(-2px); }
        .trv-seeall:hover{ text-decoration:underline; }
      `}</style>
    </div>
  );
}
