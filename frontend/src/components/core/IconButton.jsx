import React from "react";

/**
 * Trove IconButton — square/circular button holding a single icon node.
 */
export function IconButton({
  variant = "ghost",
  size = "md",
  round = false,
  disabled = false,
  label,
  badge = null,
  style = {},
  children,
  ...rest
}) {
  const sizes = { sm: 32, md: 40, lg: 46 };
  const px = sizes[size] || sizes.md;

  const variants = {
    ghost: { background: "transparent", color: "var(--text-secondary)", border: "1px solid transparent" },
    solid: { background: "var(--primary)", color: "var(--on-primary)", border: "1px solid transparent" },
    surface: { background: "var(--surface)", color: "var(--text)", border: "1px solid var(--border)", boxShadow: "var(--shadow-xs)" },
    accent: { background: "var(--accent)", color: "var(--on-accent)", border: "1px solid transparent" },
  };
  const v = variants[variant] || variants.ghost;

  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      disabled={disabled}
      data-variant={variant}
      className="trv-iconbtn"
      style={{
        position: "relative",
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        width: px, height: px,
        borderRadius: round ? "var(--radius-pill)" : "var(--radius-md)",
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.5 : 1,
        transition: "background var(--dur-fast) var(--ease-out), transform var(--dur-fast) var(--ease-out), border-color var(--dur-fast)",
        ...v, ...style,
      }}
      {...rest}
    >
      {children}
      {badge != null && (
        <span style={{
          position: "absolute", top: -3, right: -3, minWidth: 17, height: 17, padding: "0 4px",
          borderRadius: "var(--radius-pill)", background: "var(--accent)", color: "var(--on-accent)",
          fontFamily: "var(--font-mono)", fontSize: 10, fontWeight: 700, lineHeight: "17px",
          display: "flex", alignItems: "center", justifyContent: "center",
          border: "2px solid var(--surface)",
        }}>{badge}</span>
      )}
      <style>{`
        .trv-iconbtn[data-variant="ghost"]:hover:not(:disabled){ background:var(--surface-2); color:var(--text); }
        .trv-iconbtn[data-variant="surface"]:hover:not(:disabled){ background:var(--surface-2); }
        .trv-iconbtn[data-variant="solid"]:hover:not(:disabled){ background:var(--primary-hover); }
        .trv-iconbtn[data-variant="accent"]:hover:not(:disabled){ background:var(--accent-hover); }
        .trv-iconbtn:active:not(:disabled){ transform:scale(0.92); }
      `}</style>
    </button>
  );
}
