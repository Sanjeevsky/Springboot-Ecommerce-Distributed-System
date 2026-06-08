import React from "react";

/**
 * Trove PriceTag — formatted price with optional compare-at and discount.
 */
export function PriceTag({
  amount,
  compareAt = null,
  currency = "$",
  size = "md",
  align = "left",
  showDiscount = true,
  style = {},
}) {
  const sizes = {
    sm: { main: 16, cur: 11, cmp: 12 },
    md: { main: 22, cur: 13, cmp: 14 },
    lg: { main: 30, cur: 16, cmp: 16 },
    xl: { main: 40, cur: 20, cmp: 18 },
  };
  const s = sizes[size] || sizes.md;
  const fmt = (n) => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const onSale = compareAt != null && compareAt > amount;
  const pct = onSale ? Math.round((1 - amount / compareAt) * 100) : 0;

  return (
    <div style={{ display: "flex", alignItems: "baseline", gap: 8, justifyContent: align === "right" ? "flex-end" : "flex-start", flexWrap: "wrap", ...style }}>
      <span style={{
        fontFamily: "var(--font-mono)", fontWeight: 700, letterSpacing: "-0.01em",
        fontSize: s.main, lineHeight: 1, color: onSale ? "var(--sale)" : "var(--text)",
        fontFeatureSettings: '"tnum"',
      }}>
        <span style={{ fontSize: s.cur, verticalAlign: "super", marginRight: 1 }}>{currency}</span>
        {fmt(amount)}
      </span>
      {onSale && (
        <span style={{
          fontFamily: "var(--font-mono)", fontSize: s.cmp, color: "var(--text-muted)",
          textDecoration: "line-through", fontWeight: 400,
        }}>{currency}{fmt(compareAt)}</span>
      )}
      {onSale && showDiscount && (
        <span style={{
          fontFamily: "var(--font-text)", fontSize: s.cmp - 1, fontWeight: 700,
          color: "var(--on-sale)", background: "var(--sale)",
          padding: "2px 7px", borderRadius: "var(--radius-pill)", lineHeight: 1.2,
        }}>−{pct}%</span>
      )}
    </div>
  );
}
