import React from "react";

/**
 * Trove Rating — star rating display with optional count.
 */
export function Rating({
  value = 0,
  max = 5,
  size = 15,
  showValue = false,
  count = null,
  style = {},
  ...rest
}) {
  const pct = Math.max(0, Math.min(1, value / max)) * 100;
  const star =
    "M12 2.5l2.92 5.92 6.53.95-4.72 4.6 1.11 6.5L12 17.9 6.16 20.97l1.11-6.5L2.55 9.87l6.53-.95L12 2.5z";
  const stars = Array.from({ length: max });
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 7, ...style }} {...rest}>
      <span style={{ position: "relative", display: "inline-flex", lineHeight: 0 }}>
        <span style={{ display: "inline-flex", gap: 2 }}>
          {stars.map((_, i) => (
            <svg key={i} width={size} height={size} viewBox="0 0 24 24" fill="var(--border-strong)">
              <path d={star} />
            </svg>
          ))}
        </span>
        <span style={{ position: "absolute", top: 0, left: 0, width: `${pct}%`, overflow: "hidden", display: "inline-flex", gap: 2, whiteSpace: "nowrap" }}>
          {stars.map((_, i) => (
            <svg key={i} width={size} height={size} viewBox="0 0 24 24" fill="var(--star)" style={{ flex: "none" }}>
              <path d={star} />
            </svg>
          ))}
        </span>
      </span>
      {showValue && (
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, fontWeight: 700, color: "var(--text)" }}>
          {value.toFixed(1)}
        </span>
      )}
      {count != null && (
        <span style={{ fontFamily: "var(--font-text)", fontSize: 12, color: "var(--text-muted)" }}>
          ({typeof count === "number" ? count.toLocaleString() : count})
        </span>
      )}
    </span>
  );
}
