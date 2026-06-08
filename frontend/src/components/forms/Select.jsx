import React from "react";

/**
 * Trove Select — styled native select wrapper.
 */
export function Select({
  size = "md",
  invalid = false,
  children,
  style = {},
  wrapStyle = {},
  ...rest
}) {
  const sizes = {
    sm: { h: 36, font: "var(--text-sm)", pad: 11 },
    md: { h: 44, font: "var(--text-base)", pad: 13 },
    lg: { h: 52, font: "var(--text-md)", pad: 15 },
  };
  const s = sizes[size] || sizes.md;
  return (
    <div
      className="trv-select"
      style={{
        position: "relative", display: "inline-flex", alignItems: "center",
        height: s.h, background: "var(--surface)",
        border: `1px solid ${invalid ? "var(--danger)" : "var(--border-strong)"}`,
        borderRadius: "var(--radius-md)",
        transition: "border-color var(--dur-fast), box-shadow var(--dur-fast)",
        ...wrapStyle,
      }}
    >
      <select
        style={{
          appearance: "none", WebkitAppearance: "none",
          height: "100%", width: "100%", border: "none", outline: "none",
          background: "transparent", color: "var(--text)",
          fontFamily: "var(--font-text)", fontSize: s.font, fontWeight: 500,
          padding: `0 ${s.pad + 22}px 0 ${s.pad}px`, cursor: "pointer",
          ...style,
        }}
        {...rest}
      >
        {children}
      </select>
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
        style={{ position: "absolute", right: s.pad, pointerEvents: "none", color: "var(--text-muted)" }}>
        <path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
      </svg>
      <style>{`.trv-select:focus-within{ border-color:var(--primary); box-shadow:var(--shadow-focus); }`}</style>
    </div>
  );
}
