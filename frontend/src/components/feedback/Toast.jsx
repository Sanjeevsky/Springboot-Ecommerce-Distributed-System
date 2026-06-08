import React from "react";

/**
 * Trove Toast — transient notification. Render in a fixed stack.
 */
export function Toast({
  tone = "neutral",
  title,
  message,
  icon = null,
  onClose,
  action = null,
  style = {},
}) {
  const tones = {
    neutral: { accent: "var(--ink-600)", bg: "var(--surface)" },
    success: { accent: "var(--success)", bg: "var(--surface)" },
    warning: { accent: "var(--warning)", bg: "var(--surface)" },
    danger:  { accent: "var(--danger)", bg: "var(--surface)" },
    info:    { accent: "var(--info)", bg: "var(--surface)" },
  };
  const t = tones[tone] || tones.neutral;
  return (
    <div
      role="status"
      style={{
        display: "flex", alignItems: "flex-start", gap: 12,
        width: 360, maxWidth: "calc(100vw - 32px)", padding: "14px 14px 14px 16px",
        background: t.bg, border: "1px solid var(--border)",
        borderLeft: `3px solid ${t.accent}`,
        borderRadius: "var(--radius-md)", boxShadow: "var(--shadow-lg)",
        ...style,
      }}
    >
      {icon && <span style={{ color: t.accent, flex: "none", marginTop: 1, display: "inline-flex" }}>{icon}</span>}
      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 3 }}>
        {title && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: 600, color: "var(--text)", lineHeight: 1.3 }}>{title}</span>}
        {message && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-sm)", color: "var(--text-secondary)", lineHeight: 1.4 }}>{message}</span>}
        {action && <div style={{ marginTop: 6 }}>{action}</div>}
      </div>
      {onClose && (
        <button type="button" aria-label="Dismiss" onClick={onClose}
          style={{ flex: "none", border: "none", background: "transparent", cursor: "pointer",
            color: "var(--text-muted)", padding: 2, lineHeight: 0, marginTop: 1 }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6 6 18M6 6l12 12" strokeLinecap="round"/></svg>
        </button>
      )}
    </div>
  );
}
