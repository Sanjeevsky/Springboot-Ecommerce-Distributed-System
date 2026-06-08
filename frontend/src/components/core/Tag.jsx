import React from "react";

/**
 * Trove Tag — removable/selectable chip for filters & categories.
 */
export function Tag({
  selected = false,
  removable = false,
  onRemove,
  icon = null,
  size = "md",
  style = {},
  children,
  ...rest
}) {
  const sizes = {
    sm: { h: 26, pad: "0 10px", font: "var(--text-xs)" },
    md: { h: 32, pad: "0 13px", font: "var(--text-sm)" },
  };
  const s = sizes[size] || sizes.md;
  return (
    <span
      className="trv-tag"
      data-selected={selected ? "true" : "false"}
      style={{
        display: "inline-flex", alignItems: "center", gap: 7,
        height: s.h, padding: s.pad,
        borderRadius: "var(--radius-pill)",
        fontFamily: "var(--font-text)", fontSize: s.font, fontWeight: "var(--fw-medium)",
        lineHeight: 1, cursor: "pointer", userSelect: "none",
        color: selected ? "var(--on-primary)" : "var(--text-secondary)",
        background: selected ? "var(--primary)" : "var(--surface)",
        border: `1px solid ${selected ? "transparent" : "var(--border-strong)"}`,
        transition: "background var(--dur-fast) var(--ease-out), border-color var(--dur-fast), color var(--dur-fast)",
        ...style,
      }}
      {...rest}
    >
      {icon}
      {children}
      {removable && (
        <button
          type="button"
          aria-label="Remove"
          onClick={(e) => { e.stopPropagation(); onRemove && onRemove(e); }}
          style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            width: 16, height: 16, marginRight: -3, padding: 0, border: "none",
            borderRadius: "50%", cursor: "pointer", background: "transparent",
            color: "inherit", opacity: 0.7, fontSize: 13, lineHeight: 1,
          }}
        >✕</button>
      )}
      <style>{`
        .trv-tag[data-selected="false"]:hover{ background:var(--surface-2); border-color:var(--ink-400); }
      `}</style>
    </span>
  );
}
