import React from "react";
import { Link } from "react-router-dom";
import { Camera, MessageCircle, ThumbsUp, Play } from "lucide-react";
import { Logo } from "./Logo.jsx";

const cols = [
  { h: "Shop", links: ["New arrivals", "Today's deals", "Bestsellers", "Gift cards"] },
  { h: "Help", links: ["Track your order", "Returns & refunds", "Shipping info", "Contact us"] },
  { h: "Company", links: ["About Trove", "Careers", "Sustainability", "Press"] },
  { h: "Sell", links: ["Start selling", "Seller center", "Advertise", "Partnerships"] },
];
const social = [Camera, MessageCircle, ThumbsUp, Play];

export function Footer() {
  return (
    <footer style={{ background: "var(--surface)", borderTop: "1px solid var(--border)", marginTop: 64 }}>
      <div style={{ maxWidth: 1320, margin: "0 auto", padding: "48px 24px 28px" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1.4fr repeat(4, 1fr)", gap: 32 }}>
          <div>
            <div style={{ marginBottom: 14 }}><Logo size={28} /></div>
            <p style={{ color: "var(--text-muted)", fontSize: 14, lineHeight: 1.55, maxWidth: 260 }}>
              A world of things. Buy and sell almost anything, from trusted sellers, in one calm storefront.
            </p>
            <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
              {social.map((Ic, i) => (
                <a key={i} href="#" onClick={(e) => e.preventDefault()} className="trv-social"
                  style={{ width: 34, height: 34, borderRadius: 999, border: "1px solid var(--border)", display: "inline-flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)" }}>
                  <Ic size={16} />
                </a>
              ))}
            </div>
          </div>
          {cols.map((c) => (
            <div key={c.h}>
              <h4 style={{ fontFamily: "var(--font-text)", fontSize: 13, fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.06em", color: "var(--text)", margin: "0 0 14px" }}>{c.h}</h4>
              <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 10 }}>
                {c.links.map((l) => (
                  <li key={l}><a href="#" onClick={(e) => e.preventDefault()} className="trv-foot-link" style={{ color: "var(--text-muted)", fontSize: 14 }}>{l}</a></li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12, marginTop: 40, paddingTop: 22, borderTop: "1px solid var(--border)" }}>
          <span style={{ color: "var(--text-faint)", fontSize: 13 }}>© 2026 Trove, Inc. · Protected payments · Verified sellers</span>
          <div style={{ display: "flex", gap: 18 }}>
            {["Privacy", "Terms", "Cookies", "Accessibility"].map((l) => (
              <a key={l} href="#" onClick={(e) => e.preventDefault()} className="trv-foot-link" style={{ color: "var(--text-faint)", fontSize: 13 }}>{l}</a>
            ))}
          </div>
        </div>
      </div>
      <style>{`.trv-foot-link:hover{ color:var(--primary) !important; } .trv-social:hover{ border-color:var(--primary) !important; color:var(--primary) !important; }`}</style>
    </footer>
  );
}
