import React from "react";

/**
 * Trove Switch — on/off toggle.
 */
export function Switch({
  checked = false,
  disabled = false,
  size = "md",
  label,
  onChange,
  style = {},
  ...rest
}) {
  const sizes = { sm: { w: 36, h: 20, k: 14 }, md: { w: 44, h: 25, k: 19 } };
  const s = sizes[size] || sizes.md;
  return (
    <label style={{ display: "inline-flex", alignItems: "center", gap: 10, cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.5 : 1, ...style }} {...rest}>
      <span
        onClick={() => !disabled && onChange && onChange(!checked)}
        style={{
          position: "relative", flex: "none", width: s.w, height: s.h,
          borderRadius: "var(--radius-pill)",
          background: checked ? "var(--primary)" : "var(--ink-300)",
          transition: "background var(--dur-base) var(--ease-out)",
        }}
      >
        <span style={{
          position: "absolute", top: (s.h - s.k) / 2, left: checked ? s.w - s.k - (s.h - s.k) / 2 : (s.h - s.k) / 2,
          width: s.k, height: s.k, borderRadius: "50%", background: "#fff",
          boxShadow: "var(--shadow-sm)", transition: "left var(--dur-base) var(--ease-spring)",
        }} />
      </span>
      {label && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: 500, color: "var(--text)" }}>{label}</span>}
    </label>
  );
}
