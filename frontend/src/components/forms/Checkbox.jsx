import React from "react";

/**
 * Trove Checkbox — controlled checkbox with label.
 */
export function Checkbox({
  checked = false,
  indeterminate = false,
  disabled = false,
  label,
  description,
  onChange,
  style = {},
  ...rest
}) {
  const on = checked || indeterminate;
  return (
    <label
      style={{
        display: "inline-flex", alignItems: description ? "flex-start" : "center", gap: 10,
        cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.5 : 1, ...style,
      }}
      {...rest}
    >
      <span
        style={{
          flex: "none", width: 20, height: 20, marginTop: description ? 1 : 0,
          borderRadius: "var(--radius-xs)",
          border: `1.5px solid ${on ? "var(--primary)" : "var(--border-strong)"}`,
          background: on ? "var(--primary)" : "var(--surface)",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
          transition: "background var(--dur-fast) var(--ease-out), border-color var(--dur-fast)",
          color: "var(--on-primary)",
        }}
      >
        {indeterminate ? (
          <svg width="12" height="12" viewBox="0 0 12 12"><rect x="2" y="5" width="8" height="2" rx="1" fill="currentColor"/></svg>
        ) : checked ? (
          <svg width="13" height="13" viewBox="0 0 14 14" fill="none"><path d="M3 7.5l2.5 2.5L11 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>
        ) : null}
      </span>
      <input type="checkbox" checked={checked} disabled={disabled} onChange={onChange}
        style={{ position: "absolute", opacity: 0, width: 0, height: 0 }} />
      {(label || description) && (
        <span style={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {label && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: 500, color: "var(--text)", lineHeight: 1.35 }}>{label}</span>}
          {description && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", color: "var(--text-muted)", lineHeight: 1.4 }}>{description}</span>}
        </span>
      )}
    </label>
  );
}
