import React from "react";

/**
 * Trove Breadcrumb — path navigation with chevron separators.
 */
export function Breadcrumb({ items = [], style = {} }) {
  return (
    <nav style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 4, ...style }}>
      {items.map((it, i) => {
        const last = i === items.length - 1;
        return (
          <span key={i} style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
            <a href={it.href || "#"}
              style={{
                fontFamily: "var(--font-text)", fontSize: "var(--text-sm)",
                fontWeight: last ? 600 : 500,
                color: last ? "var(--text)" : "var(--text-muted)",
                pointerEvents: last ? "none" : "auto",
                transition: "color var(--dur-fast)",
              }}
              onMouseOver={(e) => { if (!last) e.currentTarget.style.color = "var(--primary)"; }}
              onMouseOut={(e) => { if (!last) e.currentTarget.style.color = "var(--text-muted)"; }}
            >{it.label}</a>
            {!last && (
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--text-faint)" strokeWidth="2">
                <path d="M9 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            )}
          </span>
        );
      })}
    </nav>
  );
}
