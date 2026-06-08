import React from "react";

/**
 * Trove QuantityStepper — −/value/+ control for cart quantities.
 */
export function QuantityStepper({
  value = 1,
  min = 1,
  max = 99,
  size = "md",
  onChange,
  style = {},
  ...rest
}) {
  const sizes = { sm: { h: 34, w: 30, font: 13 }, md: { h: 42, w: 38, font: 15 } };
  const s = sizes[size] || sizes.md;
  const set = (n) => { const v = Math.max(min, Math.min(max, n)); onChange && onChange(v); };
  const btn = {
    width: s.w, height: "100%", border: "none", background: "transparent",
    cursor: "pointer", color: "var(--text)", fontSize: 18, lineHeight: 1,
    display: "inline-flex", alignItems: "center", justifyContent: "center",
  };
  return (
    <div
      style={{
        display: "inline-flex", alignItems: "center", height: s.h,
        background: "var(--surface)", border: "1px solid var(--border-strong)",
        borderRadius: "var(--radius-md)", overflow: "hidden", ...style,
      }}
      {...rest}
    >
      <button type="button" aria-label="Decrease" style={{ ...btn, opacity: value <= min ? 0.35 : 1 }}
        disabled={value <= min} onClick={() => set(value - 1)}>−</button>
      <span style={{
        minWidth: s.w, textAlign: "center", fontFamily: "var(--font-mono)", fontWeight: 700,
        fontSize: s.font, color: "var(--text)", borderLeft: "1px solid var(--border)",
        borderRight: "1px solid var(--border)", height: "100%", display: "inline-flex",
        alignItems: "center", justifyContent: "center", padding: "0 4px",
      }}>{value}</span>
      <button type="button" aria-label="Increase" style={{ ...btn, opacity: value >= max ? 0.35 : 1 }}
        disabled={value >= max} onClick={() => set(value + 1)}>+</button>
    </div>
  );
}
