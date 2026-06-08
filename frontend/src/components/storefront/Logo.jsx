import React from "react";
import { Link, useNavigate } from "react-router-dom";

// Trove gem logo mark + wordmark.
export function Logo({ size = 30, showWord = true, dark }) {
  const navigate = useNavigate();
  return (
    <Link to="/" style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
      <svg width={size} height={size} viewBox="0 0 72 72" style={{ flex: "none" }}>
        <rect width="72" height="72" rx="18" fill="var(--primary)" />
        <path d="M25 27 H47 L53 34 L36 56 L19 34 Z" fill="#FBFAF7" />
        <path d="M25 27 L30 34 H19 Z" fill="#C8EAD6" />
        <path d="M47 27 L42 34 H53 Z" fill="#C8EAD6" />
        <path d="M30 34 H42 L36 57 Z" fill="#E29D22" />
        <path d="M30 34 L36 57 L19 34 Z" fill="#EFECE6" />
      </svg>
      {showWord && (
        <span style={{ fontFamily: "var(--font-display)", fontSize: size * 0.83, fontWeight: 700, letterSpacing: "-0.04em", color: dark ? "var(--paper)" : "var(--text)" }}>
          trove
        </span>
      )}
    </Link>
  );
}
