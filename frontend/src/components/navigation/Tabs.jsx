import React from "react";

/**
 * Trove Tabs — underline tab bar. Controlled via value/onChange.
 */
export function Tabs({ items = [], value, onChange, size = "md", style = {} }) {
  const [internal, setInternal] = React.useState(items[0] && items[0].id);
  const active = value !== undefined ? value : internal;
  const set = (id) => { if (value === undefined) setInternal(id); onChange && onChange(id); };
  const font = size === "sm" ? "var(--text-sm)" : "var(--text-base)";
  return (
    <div style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--border)", ...style }}>
      {items.map((it) => {
        const on = it.id === active;
        return (
          <button key={it.id} type="button" onClick={() => set(it.id)}
            style={{
              position: "relative", border: "none", background: "transparent", cursor: "pointer",
              padding: size === "sm" ? "8px 12px" : "11px 14px",
              fontFamily: "var(--font-text)", fontSize: font, fontWeight: 600,
              color: on ? "var(--text)" : "var(--text-muted)",
              transition: "color var(--dur-fast)",
              display: "inline-flex", alignItems: "center", gap: 7,
            }}>
            {it.icon}
            {it.label}
            {it.count != null && (
              <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, fontWeight: 700,
                color: on ? "var(--primary)" : "var(--text-faint)",
                background: on ? "var(--primary-subtle)" : "var(--surface-2)",
                padding: "1px 6px", borderRadius: "var(--radius-pill)" }}>{it.count}</span>
            )}
            <span style={{
              position: "absolute", left: 8, right: 8, bottom: -1, height: 2.5,
              borderRadius: "2px 2px 0 0", background: "var(--primary)",
              transform: on ? "scaleX(1)" : "scaleX(0)",
              transition: "transform var(--dur-base) var(--ease-out)",
            }} />
          </button>
        );
      })}
    </div>
  );
}
