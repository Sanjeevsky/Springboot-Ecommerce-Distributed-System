import React, { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Tag, Select, Checkbox, Breadcrumb } from "../components/index.js";
import { NavCard } from "../components/storefront/NavCard.jsx";
import { useStore } from "../store/StoreContext.jsx";
import { catalog } from "../lib/services.js";

function FilterGroup({ title, children }) {
  return (
    <div style={{ paddingBottom: 20, marginBottom: 20, borderBottom: "1px solid var(--border)" }}>
      <h4 style={{ fontFamily: "var(--font-text)", fontSize: 14, fontWeight: 700, color: "var(--text)", margin: "0 0 14px" }}>{title}</h4>
      <div style={{ display: "flex", flexDirection: "column", gap: 11 }}>{children}</div>
    </div>
  );
}

export default function Listing() {
  const { categoryId } = useParams();
  const [params] = useSearchParams();
  const q = params.get("q");
  const { wishlist, toggleWish, addToCart } = useStore();
  const [all, setAll] = useState([]);
  const [categories, setCategories] = useState([]);
  const [sort, setSort] = useState("popular");
  const [onlyFree, setOnlyFree] = useState(false);
  const [priceRanges, setPriceRanges] = useState([]);
  const [selectedBrands, setSelectedBrands] = useState([]);
  const [minRating, setMinRating] = useState(0);

  useEffect(() => {
    if (q != null) catalog.search(q).then(setAll);
    else catalog.list({ size: 50 }).then(setAll);
  }, [q, categoryId]);
  useEffect(() => { catalog.categories().then(setCategories); }, []);

  const cat = categories.find((c) => c.id === categoryId);
  // Also derive title from first product's catLabel when category nav hasn't loaded yet
  const catLabelFallback = all.length > 0 ? all[0]?.catLabel : "";
  const title = q != null ? `Results for "${q}"` : cat ? cat.label : catLabelFallback || "All products";

  const PRICE_MAP = { "Under $50": [0, 50], "$50–$150": [50, 150], "$150–$400": [150, 400], "$400+": [400, Infinity] };
  const togglePriceRange = (r) => setPriceRanges((s) => s.includes(r) ? s.filter((x) => x !== r) : [...s, r]);
  const toggleBrand = (b) => setSelectedBrands((s) => s.includes(b) ? s.filter((x) => x !== b) : [...s, b]);

  let list = all.slice();
  if (categoryId && categoryId !== "all" && !q) list = list.filter((p) => p.cat === categoryId);
  if (onlyFree) list = list.filter((p) => p.freeShipping);
  if (priceRanges.length > 0) list = list.filter((p) => priceRanges.some((r) => { const [lo, hi] = PRICE_MAP[r]; return p.price >= lo && p.price < hi; }));
  if (selectedBrands.length > 0) list = list.filter((p) => selectedBrands.includes(p.brand));
  if (minRating > 0) list = list.filter((p) => p.rating >= minRating);
  if (sort === "low") list.sort((a, b) => a.price - b.price);
  if (sort === "high") list.sort((a, b) => b.price - a.price);
  if (sort === "rating") list.sort((a, b) => b.rating - a.rating);

  const brands = [...new Set(all.map((p) => p.brand))].slice(0, 6);
  const withHandlers = (p) => ({ ...p, wished: !!wishlist[p.id], onWish: () => toggleWish(p.id), onAdd: () => addToCart(p) });

  return (
    <div style={{ maxWidth: 1320, margin: "0 auto", padding: "20px 24px 0" }}>
      <Breadcrumb items={[{ label: "Home", href: "/" }, { label: "All categories", href: "/c/all" }, { label: title }]} />

      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", margin: "16px 0 22px", gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 34, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)" }}>{title}</h1>
          <p style={{ color: "var(--text-muted)", fontSize: 14, marginTop: 4 }}>{list.length} results</p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 13, color: "var(--text-muted)", fontWeight: 500 }}>Sort</span>
          <Select size="sm" value={sort} onChange={(e) => setSort(e.target.value)} wrapStyle={{ minWidth: 180 }}>
            <option value="popular">Most popular</option>
            <option value="low">Price: low to high</option>
            <option value="high">Price: high to low</option>
            <option value="rating">Top rated</option>
          </Select>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "240px 1fr", gap: 28, alignItems: "start" }}>
        <aside style={{ position: "sticky", top: 130 }}>
          <FilterGroup title="Price">
            {["Under $50", "$50–$150", "$150–$400", "$400+"].map((r) => (
              <Checkbox key={r} checked={priceRanges.includes(r)} onChange={() => togglePriceRange(r)} label={r} />
            ))}
          </FilterGroup>
          <FilterGroup title="Brand">
            {brands.map((b) => (
              <Checkbox key={b} checked={selectedBrands.includes(b)} onChange={() => toggleBrand(b)} label={b} />
            ))}
          </FilterGroup>
          <FilterGroup title="Rating">
            {[4, 3].map((r) => (
              <Checkbox key={r} checked={minRating === r} onChange={() => setMinRating((cur) => cur === r ? 0 : r)} label={`${r} stars & up`} />
            ))}
          </FilterGroup>
          <FilterGroup title="Shipping">
            <Checkbox checked={onlyFree} onChange={(e) => setOnlyFree(e.target.checked)} label="Free shipping" />
          </FilterGroup>
        </aside>

        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 }}>
          {list.map((p) => <NavCard key={p.id} product={withHandlers(p)} />)}
        </div>
      </div>
    </div>
  );
}
