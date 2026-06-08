import React, { useState, useEffect } from "react";
import { Heart } from "lucide-react";
import { Button, Select, ProductCard } from "../../components/index.js";
import { useStore } from "../../store/StoreContext.jsx";
import { useNavigate } from "react-router-dom";
import { wishlist as wishlistApi } from "../../lib/services.js";

export default function Wishlist() {
  const { toggleWish, addToCart } = useStore();
  const navigate = useNavigate();
  const [sort, setSort] = useState("recent");
  const [saved, setSaved] = useState([]);

  useEffect(() => {
    wishlistApi.get().then(setSaved).catch(() => setSaved([]));
  }, []);

  const removeFromWishlist = (id) => {
    wishlistApi.remove(id).catch(() => {});
    setSaved((s) => s.filter((p) => p.id !== id));
  };

  const sorted = [...saved].sort((a, b) => {
    if (sort === "price-low") return a.price - b.price;
    if (sort === "drops") return ((b.compareAt ?? 0) - b.price) - ((a.compareAt ?? 0) - a.price);
    return 0; // "recent" — preserve insertion order
  });

  return (
    <div>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", marginBottom: 22, gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Saved items</h1>
          <p style={{ color: "var(--text-muted)", fontSize: 15 }}>{saved.length} items · we'll tell you about price drops</p>
        </div>
        <Select size="sm" value={sort} onChange={(e) => setSort(e.target.value)} wrapStyle={{ minWidth: 180 }}>
          <option value="recent">Recently added</option>
          <option value="price-low">Price: low to high</option>
          <option value="drops">Price drops first</option>
        </Select>
      </div>

      {saved.length === 0 ? (
        <div style={{ textAlign: "center", padding: "80px 20px", border: "1px dashed var(--border-strong)", borderRadius: "var(--radius-lg)", color: "var(--text-muted)" }}>
          <Heart size={40} style={{ opacity: 0.4, margin: "0 auto" }} />
          <p style={{ marginTop: 14, fontWeight: 600, color: "var(--text)" }}>No saved items yet</p>
          <p style={{ fontSize: 14, marginTop: 4 }}>Tap the heart on anything to save it for later.</p>
          <div style={{ marginTop: 20 }}>
            <Button variant="primary" onClick={() => navigate("/c/all")}>Browse products</Button>
          </div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {sorted.map((p) => (
            <div key={p.id} style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", padding: 14, cursor: "pointer" }}
              onClick={(e) => { if (!e.target.closest("button")) navigate(`/p/${p.id}`); }}>
              <ProductCard
                {...p}
                layout="row"
                wished
                onWish={() => removeFromWishlist(p.id)}
                onAdd={() => addToCart(p)}
                href={`/p/${p.id}`}
                style={{ border: "none", borderRadius: 0, background: "transparent", boxShadow: "none" }}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
