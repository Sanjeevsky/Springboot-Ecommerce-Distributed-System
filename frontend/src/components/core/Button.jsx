import React from "react";

/**
 * Trove Button — primary commerce action.
 * Variants: primary | secondary | ghost | accent | danger | link
 * Sizes: sm | md | lg
 */
export function Button({
  variant = "primary",
  size = "md",
  block = false,
  loading = false,
  disabled = false,
  iconLeft = null,
  iconRight = null,
  type = "button",
  style = {},
  children,
  ...rest
}) {
  const sizes = {
    sm: { height: 34, padding: "0 14px", font: "var(--text-sm)", radius: "var(--radius-sm)", gap: 7 },
    md: { height: 42, padding: "0 18px", font: "var(--text-base)", radius: "var(--radius-md)", gap: 8 },
    lg: { height: 52, padding: "0 26px", font: "var(--text-md)", radius: "var(--radius-md)", gap: 10 },
  };
  const s = sizes[size] || sizes.md;

  const variants = {
    primary: {
      background: "var(--primary)", color: "var(--on-primary)",
      border: "1px solid transparent", boxShadow: "var(--shadow-xs)",
    },
    secondary: {
      background: "var(--surface)", color: "var(--text)",
      border: "1px solid var(--border-strong)", boxShadow: "var(--shadow-xs)",
    },
    ghost: {
      background: "transparent", color: "var(--text)", border: "1px solid transparent",
    },
    accent: {
      background: "var(--accent)", color: "var(--on-accent)",
      border: "1px solid transparent", boxShadow: "var(--shadow-xs)",
    },
    danger: {
      background: "var(--danger)", color: "#fff", border: "1px solid transparent",
    },
    link: {
      background: "transparent", color: "var(--primary)", border: "1px solid transparent",
      padding: 0, height: "auto", textDecoration: "none",
    },
  };
  const v = variants[variant] || variants.primary;
  const isDisabled = disabled || loading;

  return (
    <button
      type={type}
      disabled={isDisabled}
      data-variant={variant}
      className="trv-btn"
      style={{
        display: block ? "flex" : "inline-flex",
        width: block ? "100%" : "auto",
        alignItems: "center",
        justifyContent: "center",
        gap: s.gap,
        height: variant === "link" ? "auto" : s.height,
        padding: variant === "link" ? 0 : s.padding,
        font: "inherit",
        fontFamily: "var(--font-text)",
        fontSize: s.font,
        fontWeight: "var(--fw-semibold)",
        letterSpacing: "-0.005em",
        lineHeight: 1,
        borderRadius: variant === "link" ? 0 : s.radius,
        cursor: isDisabled ? "not-allowed" : "pointer",
        opacity: isDisabled ? 0.5 : 1,
        transition: "transform var(--dur-fast) var(--ease-out), background var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out)",
        whiteSpace: "nowrap",
        ...v,
        ...style,
      }}
      {...rest}
    >
      {loading && <Spinner />}
      {!loading && iconLeft}
      {children && <span>{children}</span>}
      {!loading && iconRight}
      <style>{`
        .trv-btn:hover:not(:disabled){ filter:brightness(0.97); }
        .trv-btn[data-variant="primary"]:hover:not(:disabled){ background:var(--primary-hover); filter:none; }
        .trv-btn[data-variant="accent"]:hover:not(:disabled){ background:var(--accent-hover); filter:none; }
        .trv-btn[data-variant="secondary"]:hover:not(:disabled){ background:var(--surface-2); filter:none; }
        .trv-btn[data-variant="ghost"]:hover:not(:disabled){ background:var(--surface-2); filter:none; }
        .trv-btn[data-variant="danger"]:hover:not(:disabled){ background:var(--danger-hover); filter:none; }
        .trv-btn[data-variant="link"]:hover:not(:disabled){ text-decoration:underline; filter:none; }
        .trv-btn:active:not(:disabled){ transform:translateY(0.5px) scale(0.985); }
      `}</style>
    </button>
  );
}

function Spinner() {
  return (
    <span
      aria-hidden
      style={{
        width: 15, height: 15, borderRadius: "50%",
        border: "2px solid currentColor", borderTopColor: "transparent",
        display: "inline-block", animation: "trvspin 0.7s linear infinite",
      }}
    >
      <style>{`@keyframes trvspin{to{transform:rotate(360deg)}}`}</style>
    </span>
  );
}
