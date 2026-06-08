import React from "react";
import { PriceTag } from "./PriceTag.jsx";
import { Rating } from "../core/Rating.jsx";
import { Badge } from "../core/Badge.jsx";
import { IconButton } from "../core/IconButton.jsx";

/**
 * Trove ProductCard — the marketplace's primary listing tile.
 */
export function ProductCard({
  title,
  brand,
  image,
  price,
  compareAt = null,
  rating = null,
  reviews = null,
  badge = null,          // { tone, label } or null
  freeShipping = false,
  wished = false,
  onWish,
  onAdd,
  href = "#",
  layout = "grid",       // "grid" | "row"
  style = {},
}) {
  const isRow = layout === "row";
  return (
    <div
      className="trv-pcard"
      style={{
        display: "flex", flexDirection: isRow ? "row" : "column",
        gap: isRow ? 16 : 0,
        background: "var(--surface)", border: "1px solid var(--border)",
        borderRadius: "var(--radius-lg)", overflow: "hidden",
        transition: "transform var(--dur-base) var(--ease-out), box-shadow var(--dur-base) var(--ease-out), border-color var(--dur-base)",
        ...style,
      }}
    >
      <a href={href} style={{
        position: "relative", display: "block", flex: isRow ? "none" : "auto",
        width: isRow ? 132 : "auto",
      }}>
        <div style={{
          position: "relative", aspectRatio: "1 / 1",
          background: "var(--surface-2)", overflow: "hidden",
        }}>
          {image
            ? <img className="trv-pcard-img" src={image} alt={title}
                style={{ width: "100%", height: "100%", objectFit: "cover", display: "block",
                  transition: "transform var(--dur-slow) var(--ease-out)" }} />
            : <div style={{ width: "100%", height: "100%", display: "flex", alignItems: "center",
                justifyContent: "center", color: "var(--text-faint)", fontSize: 12 }}>No image</div>}
          {badge && (
            <div style={{ position: "absolute", top: 10, left: 10 }}>
              <Badge tone={badge.tone || "sale"} variant="solid" size="sm">{badge.label}</Badge>
            </div>
          )}
        </div>
        <div style={{ position: "absolute", top: 8, right: 8 }}>
          <IconButton label={wished ? "Remove from wishlist" : "Add to wishlist"} variant="surface" round size="sm"
            onClick={(e) => { e.preventDefault(); onWish && onWish(); }}>
            <svg width="16" height="16" viewBox="0 0 24 24"
              fill={wished ? "var(--sale)" : "none"} stroke={wished ? "var(--sale)" : "currentColor"} strokeWidth="2">
              <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.6l-1-1a5.5 5.5 0 1 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"/>
            </svg>
          </IconButton>
        </div>
      </a>

      <div style={{ display: "flex", flexDirection: "column", gap: 7, padding: isRow ? "12px 14px 12px 0" : 14, flex: 1 }}>
        {brand && <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-xs)", fontWeight: 600, letterSpacing: "0.04em", textTransform: "uppercase", color: "var(--text-muted)" }}>{brand}</span>}
        <a href={href} style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-base)", fontWeight: 600, color: "var(--text)", lineHeight: 1.3, letterSpacing: "-0.005em" }}>{title}</a>
        {rating != null && (
          <Rating value={rating} size={13} count={reviews} />
        )}
        <div style={{ marginTop: 2 }}>
          <PriceTag amount={price} compareAt={compareAt} size="md" />
        </div>
        {freeShipping && (
          <span style={{ fontFamily: "var(--font-text)", fontSize: "var(--text-xs)", fontWeight: 600, color: "var(--success)", display: "inline-flex", alignItems: "center", gap: 4 }}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M1 3h15v13H1z"/><path d="M16 8h4l3 3v5h-7"/><circle cx="5.5" cy="18.5" r="2"/><circle cx="18.5" cy="18.5" r="2"/></svg>
            Free shipping
          </span>
        )}
        <button type="button" className="trv-pcard-add"
          onClick={() => onAdd && onAdd()}
          style={{
            marginTop: isRow ? 6 : "auto", alignSelf: isRow ? "flex-start" : "stretch",
            height: 38, padding: "0 16px", border: "1px solid var(--border-strong)",
            background: "var(--surface)", color: "var(--text)", cursor: "pointer",
            borderRadius: "var(--radius-md)", fontFamily: "var(--font-text)",
            fontSize: "var(--text-sm)", fontWeight: 600,
            display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 7,
            transition: "background var(--dur-fast), border-color var(--dur-fast), color var(--dur-fast)",
          }}>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
          Add to cart
        </button>
      </div>

      <style>{`
        .trv-pcard:hover{ box-shadow:var(--shadow-md); border-color:var(--border-strong); transform:translateY(-2px); }
        .trv-pcard:hover .trv-pcard-img{ transform:scale(1.05); }
        .trv-pcard-add:hover{ background:var(--primary); border-color:var(--primary); color:var(--on-primary); }
      `}</style>
    </div>
  );
}
