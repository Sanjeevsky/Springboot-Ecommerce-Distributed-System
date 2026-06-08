import React from "react";

/**
 * Trove Input — text field with optional adornments.
 */
export function Input({
  size = "md",
  invalid = false,
  iconLeft = null,
  iconRight = null,
  prefix = null,
  suffix = null,
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
      className="trv-input"
      data-invalid={invalid ? "true" : "false"}
      style={{
        display: "flex", alignItems: "center", gap: 9,
        height: s.h, padding: `0 ${s.pad}px`,
        background: "var(--surface)",
        border: `1px solid ${invalid ? "var(--danger)" : "var(--border-strong)"}`,
        borderRadius: "var(--radius-md)",
        transition: "border-color var(--dur-fast), box-shadow var(--dur-fast)",
        ...wrapStyle,
      }}
    >
      {iconLeft && <span style={{ display: "inline-flex", color: "var(--text-muted)", flex: "none" }}>{iconLeft}</span>}
      {prefix && <span style={{ color: "var(--text-muted)", fontSize: s.font, fontFamily: "var(--font-mono)" }}>{prefix}</span>}
      <input
        style={{
          flex: 1, minWidth: 0, height: "100%", border: "none", outline: "none",
          background: "transparent", color: "var(--text)",
          fontFamily: "var(--font-text)", fontSize: s.font, fontWeight: 500,
          ...style,
        }}
        {...rest}
      />
      {suffix && <span style={{ color: "var(--text-muted)", fontSize: s.font, fontFamily: "var(--font-mono)" }}>{suffix}</span>}
      {iconRight && <span style={{ display: "inline-flex", color: "var(--text-muted)", flex: "none" }}>{iconRight}</span>}
      <style>{`
        .trv-input:focus-within{ border-color:var(--primary); box-shadow:var(--shadow-focus); }
        .trv-input[data-invalid="true"]:focus-within{ box-shadow:0 0 0 3px var(--danger-subtle); }
        .trv-input input::placeholder{ color:var(--text-faint); font-weight:400; }
      `}</style>
    </div>
  );
}
