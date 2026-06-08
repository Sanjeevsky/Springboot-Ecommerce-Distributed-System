import React from "react";

/**
 * Trove Dialog — modal overlay with header, body, footer.
 */
export function Dialog({
  open = true,
  title,
  description,
  onClose,
  footer = null,
  width = 460,
  children,
}) {
  if (!open) return null;
  return (
    <div
      onClick={onClose}
      style={{
        position: "fixed", inset: 0, zIndex: 1000,
        display: "flex", alignItems: "center", justifyContent: "center", padding: 20,
        background: "var(--overlay)", backdropFilter: "blur(var(--backdrop-blur))",
        animation: "trvfade var(--dur-base) var(--ease-out)",
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
        style={{
          width, maxWidth: "100%", maxHeight: "calc(100vh - 40px)", overflow: "auto",
          background: "var(--surface)", border: "1px solid var(--border)",
          borderRadius: "var(--radius-xl)", boxShadow: "var(--shadow-xl)",
          animation: "trvpop var(--dur-base) var(--ease-spring)",
        }}
      >
        <div style={{ display: "flex", alignItems: "flex-start", gap: 16, padding: "20px 22px 0" }}>
          <div style={{ flex: 1 }}>
            {title && <h3 style={{ fontFamily: "var(--font-display)", fontSize: "var(--text-xl)", fontWeight: 600, letterSpacing: "-0.015em", color: "var(--text)", margin: 0 }}>{title}</h3>}
            {description && <p style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", color: "var(--text-secondary)", margin: "6px 0 0", lineHeight: 1.45 }}>{description}</p>}
          </div>
          {onClose && (
            <button type="button" aria-label="Close" onClick={onClose}
              style={{ flex: "none", border: "none", background: "var(--surface-2)", cursor: "pointer",
                width: 30, height: 30, borderRadius: "var(--radius-pill)", color: "var(--text-muted)",
                display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6 6 18M6 6l12 12" strokeLinecap="round"/></svg>
            </button>
          )}
        </div>
        {children && <div style={{ padding: "16px 22px 4px", fontFamily: "var(--font-text)", fontSize: "var(--text-base)", color: "var(--text-secondary)", lineHeight: 1.5 }}>{children}</div>}
        {footer && <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, padding: "16px 22px 22px" }}>{footer}</div>}
        <style>{`
          @keyframes trvfade{ from{opacity:0} to{opacity:1} }
          @keyframes trvpop{ from{opacity:0; transform:translateY(8px) scale(0.98)} to{opacity:1; transform:none} }
        `}</style>
      </div>
    </div>
  );
}
