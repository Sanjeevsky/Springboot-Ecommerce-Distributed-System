import React from "react";

/**
 * Trove Avatar — user/seller image with initials fallback.
 */
export function Avatar({
  src = "",
  name = "",
  size = "md",
  square = false,
  ring = false,
  status = null, // "online" | "away" | null
  style = {},
  ...rest
}) {
  const sizes = { xs: 24, sm: 32, md: 40, lg: 52, xl: 72 };
  const px = sizes[size] || (typeof size === "number" ? size : 40);
  const initials = name
    .split(" ")
    .map((p) => p[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  // deterministic warm tint from name
  const palette = ["var(--emerald-500)", "var(--amber-500)", "var(--info)", "var(--ink-600)", "var(--sale-500)"];
  const idx = name ? name.charCodeAt(0) % palette.length : 0;

  return (
    <span style={{ position: "relative", display: "inline-flex", flex: "none", ...style }} {...rest}>
      <span
        style={{
          width: px, height: px,
          borderRadius: square ? "var(--radius-md)" : "var(--radius-pill)",
          overflow: "hidden", display: "inline-flex", alignItems: "center", justifyContent: "center",
          background: palette[idx], color: "#fff",
          fontFamily: "var(--font-text)", fontWeight: 700,
          fontSize: Math.round(px * 0.4), letterSpacing: "-0.01em",
          boxShadow: ring ? "0 0 0 2px var(--surface), 0 0 0 4px var(--primary)" : "none",
        }}
      >
        {src ? (
          <img src={src} alt={name} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          initials || "?"
        )}
      </span>
      {status && (
        <span style={{
          position: "absolute", bottom: 0, right: 0,
          width: Math.max(8, px * 0.26), height: Math.max(8, px * 0.26),
          borderRadius: "50%", border: "2px solid var(--surface)",
          background: status === "online" ? "var(--success)" : "var(--amber-400)",
        }} />
      )}
    </span>
  );
}
