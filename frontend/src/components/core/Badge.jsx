import React from "react";

/**
 * Trove Badge — small status/label pill. Tones map to semantic colors.
 */
export function Badge({
  tone = "neutral",
  variant = "soft",
  size = "md",
  dot = false,
  icon = null,
  style = {},
  children,
  ...rest
}) {
  const tones = {
    neutral: { fg: "var(--text-secondary)", bg: "var(--surface-2)", solidBg: "var(--ink-700)", border: "var(--border)" },
    primary: { fg: "var(--primary)", bg: "var(--primary-subtle)", solidBg: "var(--primary)", border: "var(--primary-border)" },
    accent:  { fg: "var(--amber-700)", bg: "var(--accent-subtle)", solidBg: "var(--accent)", border: "var(--amber-200)" },
    success: { fg: "var(--success)", bg: "var(--success-subtle)", solidBg: "var(--success)", border: "transparent" },
    warning: { fg: "var(--amber-700)", bg: "var(--warning-subtle)", solidBg: "var(--warning)", border: "transparent" },
    danger:  { fg: "var(--danger)", bg: "var(--danger-subtle)", solidBg: "var(--danger)", border: "transparent" },
    info:    { fg: "var(--info)", bg: "var(--info-subtle)", solidBg: "var(--info)", border: "transparent" },
    sale:    { fg: "var(--sale)", bg: "var(--sale-subtle)", solidBg: "var(--sale)", border: "transparent" },
  };
  const t = tones[tone] || tones.neutral;
  const sizes = {
    sm: { font: "var(--text-2xs)", pad: "2px 7px", h: 18 },
    md: { font: "var(--text-xs)", pad: "3px 9px", h: 22 },
    lg: { font: "var(--text-sm)", pad: "5px 12px", h: 28 },
  };
  const s = sizes[size] || sizes.md;
  const solid = variant === "solid";

  return (
    <span
      style={{
        display: "inline-flex", alignItems: "center", gap: 5,
        height: s.h, padding: s.pad,
        borderRadius: "var(--radius-pill)",
        fontFamily: "var(--font-text)", fontSize: s.font, fontWeight: "var(--fw-semibold)",
        letterSpacing: "0.005em", lineHeight: 1, whiteSpace: "nowrap",
        color: solid ? "#fff" : t.fg,
        background: solid ? t.solidBg : t.bg,
        border: solid ? "1px solid transparent" : `1px solid ${t.border}`,
        ...style,
      }}
      {...rest}
    >
      {dot && <span style={{ width: 6, height: 6, borderRadius: "50%", background: solid ? "#fff" : t.fg }} />}
      {icon}
      {children}
    </span>
  );
}
