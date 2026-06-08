import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ShoppingBag, Truck, RotateCcw, ShieldCheck, BadgeCheck, Star, Heart } from "lucide-react";
import { Button, Badge, Rating, PriceTag, QuantityStepper, Tabs, Breadcrumb, Avatar } from "../components/index.js";
import { NavCard } from "../components/storefront/NavCard.jsx";
import { useStore } from "../store/StoreContext.jsx";
import { catalog, reviews as reviewsApi } from "../lib/services.js";
import { money } from "../lib/format.js";

const GAL = [
  "1484704849700-f032a568e944", "1583394838336-acd977736f90", "1545127398-14699f92334b",
];

export default function Product() {
  const navigate = useNavigate();
  const { productId } = useParams();
  const { wishlist, toggleWish, addToCart } = useStore();
  const [p, setP] = useState(null);
  const [all, setAll] = useState([]);
  const [qty, setQty] = useState(1);
  const [tab, setTab] = useState("overview");
  const [activeImg, setActiveImg] = useState(0);

  useEffect(() => {
    catalog.get(productId).then((prod) => { setP(prod); setQty(1); setActiveImg(0); setTab("overview"); window.scrollTo(0, 0); });
    catalog.list().then(setAll);
  }, [productId]);

  if (!p) return <div style={{ maxWidth: 1320, margin: "0 auto", padding: 48, color: "var(--text-muted)" }}>Loading…</div>;

  const gallery = [p.image, ...GAL.map((id) => `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=600&q=72`)];
  const wished = !!wishlist[p.id];
  const related = all.filter((x) => x.id !== p.id).slice(0, 5);
  const withHandlers = (x) => ({ ...x, wished: !!wishlist[x.id], onWish: () => toggleWish(x.id), onAdd: () => addToCart(x) });

  return (
    <div style={{ maxWidth: 1320, margin: "0 auto", padding: "20px 24px 0" }}>
      <Breadcrumb items={[{ label: "Home", href: "/" }, { label: p.brand, href: "/c/all" }, { label: p.title }]} />

      <div style={{ display: "grid", gridTemplateColumns: "1.1fr 1fr", gap: 44, marginTop: 18 }}>
        <div style={{ display: "flex", gap: 14 }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {gallery.map((g, i) => (
              <button key={i} onClick={() => setActiveImg(i)} style={{ width: 70, height: 70, borderRadius: "var(--radius-md)", overflow: "hidden", border: `2px solid ${i === activeImg ? "var(--primary)" : "var(--border)"}`, cursor: "pointer", padding: 0, background: "var(--surface-2)" }}>
                <img src={g} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
              </button>
            ))}
          </div>
          <div style={{ flex: 1, position: "relative", borderRadius: "var(--radius-xl)", overflow: "hidden", background: "var(--surface-2)", aspectRatio: "4 / 5" }}>
            <img src={gallery[activeImg]} alt={p.title} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
            {p.badge && <div style={{ position: "absolute", top: 16, left: 16 }}><Badge tone={p.badge.tone} variant="solid" size="lg">{p.badge.label}</Badge></div>}
          </div>
        </div>

        <div style={{ paddingTop: 4 }}>
          <div className="t-eyebrow" style={{ color: "var(--text-muted)", marginBottom: 8 }}>{p.brand}</div>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 32, fontWeight: 700, letterSpacing: "-0.025em", lineHeight: 1.1, color: "var(--text)" }}>{p.title}</h1>
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 12 }}>
            <Rating value={p.rating} showValue count={p.reviews} size={16} />
            <span style={{ color: "var(--border-strong)" }}>·</span>
            <a href="#reviews" onClick={(e) => { e.preventDefault(); setTab("reviews"); }} style={{ color: "var(--primary)", fontSize: 14, fontWeight: 600 }}>See reviews</a>
          </div>

          <div style={{ marginTop: 20 }}>
            <PriceTag amount={p.price} compareAt={p.compareAt} size="xl" />
            {p.compareAt && <div style={{ color: "var(--success)", fontSize: 14, fontWeight: 600, marginTop: 6 }}>You save {money(p.compareAt - p.price)}</div>}
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 16 }}>
            <span style={{ width: 9, height: 9, borderRadius: 999, background: p.stock <= 5 ? "var(--warning)" : "var(--success)" }} />
            <span style={{ fontSize: 14, fontWeight: 600, color: p.stock <= 5 ? "var(--warning)" : "var(--success)" }}>{p.stock <= 5 ? `Only ${p.stock} left` : "In stock"}</span>
            <span style={{ color: "var(--text-muted)", fontSize: 14 }}>· Ships in 1 business day</span>
          </div>

          <div style={{ marginTop: 22 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text)", marginBottom: 10 }}>Color: <span style={{ color: "var(--text-secondary)", fontWeight: 500 }}>Graphite</span></div>
            <div style={{ display: "flex", gap: 10 }}>
              {["#2D2925", "#FBFAF7", "#0E8C52", "#2D6FD0"].map((c, i) => (
                <button key={c} style={{ width: 38, height: 38, borderRadius: 999, background: c, border: `2px solid ${i === 0 ? "var(--primary)" : "var(--border)"}`, cursor: "pointer", boxShadow: "inset 0 0 0 2px var(--surface)" }} />
              ))}
            </div>
          </div>

          <div style={{ display: "flex", gap: 12, marginTop: 26, alignItems: "stretch" }}>
            <QuantityStepper value={qty} max={p.stock} onChange={setQty} />
            <Button variant="primary" size="lg" block onClick={() => addToCart(p, qty)} iconLeft={<ShoppingBag size={18} />} style={{ flex: 1 }}>
              Add to cart · {money(p.price * qty)}
            </Button>
          </div>
          <div style={{ display: "flex", gap: 12, marginTop: 12 }}>
            <Button variant="accent" size="lg" block onClick={() => { addToCart(p, qty); navigate("/checkout"); }}>Buy now</Button>
            <Button variant="secondary" size="lg" onClick={() => toggleWish(p.id)} iconLeft={<Heart size={18} fill={wished ? "var(--sale)" : "none"} color={wished ? "var(--sale)" : "currentColor"} />}>Save</Button>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 26, padding: 16, border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface-2)" }}>
            {[[Truck, "Free shipping", "Arrives Thu, Jun 12"], [RotateCcw, "Free returns", "Within 30 days"], [ShieldCheck, "Protected payment", "Buyer guarantee"], [BadgeCheck, "Verified seller", `${p.brand} Official`]].map(([Ic, t, s]) => (
              <div key={t} style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
                <Ic size={18} style={{ color: "var(--primary)", flex: "none", marginTop: 2 }} />
                <div><div style={{ fontSize: 13.5, fontWeight: 600, color: "var(--text)" }}>{t}</div><div style={{ fontSize: 12.5, color: "var(--text-muted)" }}>{s}</div></div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ marginTop: 52 }} id="reviews">
        <Tabs value={tab} onChange={setTab} items={[
          { id: "overview", label: "Overview" },
          { id: "specs", label: "Specifications" },
          { id: "reviews", label: "Reviews", count: p.reviews },
        ]} />
        <div style={{ padding: "28px 0", maxWidth: 760 }}>
          {tab === "overview" && (
            <div style={{ color: "var(--text-secondary)", fontSize: 16, lineHeight: 1.65 }}>
              <p>Engineered for all-day use, the {p.title.toLowerCase()} pairs thoughtful design with dependable performance. Built to be the one you reach for first.</p>
              <ul style={{ marginTop: 16, paddingLeft: 20, display: "flex", flexDirection: "column", gap: 8 }}>
                <li>Premium, durable materials</li><li>Backed by a 2-year warranty</li><li>Free 30-day returns</li>
              </ul>
            </div>
          )}
          {tab === "specs" && (
            <div style={{ display: "grid", gridTemplateColumns: "200px 1fr" }}>
              {[["Brand", p.brand], ["Category", p.cat], ["Price", money(p.price)], ["In stock", String(p.stock)], ["Warranty", "2 years"], ["SKU", "TRV-" + p.id.toUpperCase()]].map(([k, v]) => (
                <React.Fragment key={k}>
                  <div style={{ padding: "12px 0", borderBottom: "1px solid var(--border)", fontWeight: 600, color: "var(--text)", fontSize: 14.5, textTransform: k === "Category" ? "capitalize" : "none" }}>{k}</div>
                  <div style={{ padding: "12px 0", borderBottom: "1px solid var(--border)", color: "var(--text-secondary)", fontSize: 14.5, fontFamily: k === "SKU" ? "var(--font-mono)" : "inherit", textTransform: k === "Category" ? "capitalize" : "none" }}>{v}</div>
                </React.Fragment>
              ))}
            </div>
          )}
          {tab === "reviews" && <Reviews product={p} />}
        </div>
      </div>

      <section style={{ marginTop: 24 }}>
        <h2 style={{ fontFamily: "var(--font-display)", fontSize: 26, fontWeight: 700, letterSpacing: "-0.025em", color: "var(--text)", marginBottom: 18 }}>You might also like</h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 16 }}>
          {related.map((r) => <NavCard key={r.id} product={withHandlers(r)} />)}
        </div>
      </section>
    </div>
  );
}

function Reviews({ product }) {
  const [list, setList] = useState([]);
  const [summary, setSummary] = useState(null);

  useEffect(() => {
    reviewsApi.forProduct(product.id).then(setList).catch(() => {});
    reviewsApi.summary(product.id).then(setSummary).catch(() => {});
  }, [product.id]);

  const avg = summary?.average ?? product.rating;
  const count = summary?.count ?? product.reviews;

  const starCounts = [5, 4, 3, 2, 1].map((s) => {
    const n = list.filter((r) => Math.round(r.rating ?? r.stars ?? 0) === s).length;
    return [String(s), list.length > 0 ? Math.round((n / list.length) * 100) : 0];
  });

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "240px 1fr", gap: 40, alignItems: "start", marginBottom: 28 }}>
        <div style={{ textAlign: "center", padding: "8px 0" }}>
          <div style={{ fontFamily: "var(--font-mono)", fontSize: 52, fontWeight: 700, color: "var(--text)", lineHeight: 1 }}>{avg.toFixed(1)}</div>
          <div style={{ margin: "8px 0 6px", display: "flex", justifyContent: "center" }}><Rating value={avg} size={18} /></div>
          <div style={{ color: "var(--text-muted)", fontSize: 14 }}>{count.toLocaleString()} reviews</div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {starCounts.map(([star, pct]) => (
            <div key={star} style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span style={{ fontSize: 13, color: "var(--text-muted)", width: 12, fontFamily: "var(--font-mono)" }}>{star}</span>
              <Star size={13} style={{ color: "var(--star)", fill: "var(--star)" }} />
              <div style={{ flex: 1, height: 8, background: "var(--surface-inset)", borderRadius: 999, overflow: "hidden" }}>
                <div style={{ width: pct + "%", height: "100%", background: "var(--star)" }} />
              </div>
              <span style={{ fontSize: 13, color: "var(--text-muted)", width: 34, textAlign: "right", fontFamily: "var(--font-mono)" }}>{pct}%</span>
            </div>
          ))}
        </div>
      </div>
      {list.length === 0 ? (
        <div style={{ color: "var(--text-muted)", fontSize: 14, padding: "20px 0" }}>No reviews yet for this product.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 22 }}>
          {list.map((r, i) => {
            const elapsed = r.createdAt ? (() => {
              const m = Math.floor((Date.now() - new Date(r.createdAt).getTime()) / 60000);
              if (m < 60) return `${m}m ago`;
              if (m < 1440) return `${Math.floor(m / 60)}h ago`;
              return `${Math.floor(m / 1440)}d ago`;
            })() : "";
            return (
              <div key={r.id ?? i} style={{ paddingBottom: 22, borderBottom: "1px solid var(--border)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
                  <Avatar name={r.userId || r.reviewerName || "User"} size="sm" />
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text)" }}>{r.userId || r.reviewerName || "Verified buyer"}</div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <Rating value={r.rating ?? r.stars ?? 5} size={12} />
                      <span style={{ fontSize: 12.5, color: "var(--text-faint)" }}>{elapsed}</span>
                      <Badge tone="success" size="sm">Verified purchase</Badge>
                    </div>
                  </div>
                </div>
                {r.title && <div style={{ fontSize: 15, fontWeight: 600, color: "var(--text)", marginBottom: 4 }}>{r.title}</div>}
                <p style={{ fontSize: 14.5, color: "var(--text-secondary)", lineHeight: 1.55 }}>{r.comment || r.body || ""}</p>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
