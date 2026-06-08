import React from "react";
import { useNavigate } from "react-router-dom";
import { X, ShoppingBag, Trash2, ArrowRight, Check } from "lucide-react";
import { IconButton, QuantityStepper, Button, Badge } from "../index.js";
import { useStore } from "../../store/StoreContext.jsx";
import { money } from "../../lib/format.js";

export function CartDrawer({ open, onClose }) {
  const navigate = useNavigate();
  const { cart, setQty, removeItem, cartSubtotal } = useStore();
  const freeAt = 50;
  const remaining = Math.max(0, freeAt - cartSubtotal);
  const pct = Math.min(100, (cartSubtotal / freeAt) * 100);
  const totalQty = cart.reduce((s, i) => s + i.qty, 0);

  const goCheckout = () => { onClose(); navigate("/checkout"); };

  return (
    <>
      <div onClick={onClose} style={{ position: "fixed", inset: 0, zIndex: 900, background: "var(--overlay)", backdropFilter: "blur(var(--backdrop-blur))", opacity: open ? 1 : 0, pointerEvents: open ? "auto" : "none", transition: "opacity 200ms var(--ease-out)" }} />
      <aside style={{ position: "fixed", top: 0, right: 0, bottom: 0, width: 420, maxWidth: "100vw", zIndex: 1000, background: "var(--surface)", boxShadow: "var(--shadow-xl)", transform: open ? "translateX(0)" : "translateX(100%)", transition: "transform 280ms var(--ease-out)", display: "flex", flexDirection: "column" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "18px 20px", borderBottom: "1px solid var(--border)" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h3 style={{ fontFamily: "var(--font-display)", fontSize: 20, fontWeight: 700, letterSpacing: "-0.02em", color: "var(--text)" }}>Your cart</h3>
            <Badge tone="neutral">{totalQty}</Badge>
          </div>
          <IconButton label="Close" variant="ghost" onClick={onClose}><X size={20} /></IconButton>
        </div>

        <div style={{ padding: "14px 20px", background: "var(--primary-subtle)", borderBottom: "1px solid var(--border)" }}>
          <div style={{ fontSize: 13, color: "var(--text)", fontWeight: 500, marginBottom: 8 }}>
            {remaining > 0 ? <>Add <b>{money(remaining)}</b> for free shipping</> : <><Check size={14} style={{ verticalAlign: "-2px" }} /> You've unlocked free shipping</>}
          </div>
          <div style={{ height: 6, background: "var(--surface)", borderRadius: 999, overflow: "hidden" }}>
            <div style={{ width: pct + "%", height: "100%", background: "var(--primary)", transition: "width 300ms var(--ease-out)" }} />
          </div>
        </div>

        <div style={{ flex: 1, overflowY: "auto", padding: "8px 20px" }}>
          {cart.length === 0 && (
            <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--text-muted)" }}>
              <ShoppingBag size={40} style={{ opacity: 0.4 }} />
              <p style={{ marginTop: 14, fontWeight: 600, color: "var(--text)" }}>Your cart is empty</p>
              <p style={{ fontSize: 14, marginTop: 4 }}>Find something you love.</p>
            </div>
          )}
          {cart.map((it) => (
            <div key={it.id} style={{ display: "flex", gap: 12, padding: "16px 0", borderBottom: "1px solid var(--border)" }}>
              <div style={{ width: 72, height: 72, borderRadius: "var(--radius-md)", overflow: "hidden", flex: "none", background: "var(--surface-2)" }}>
                <img src={it.image} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.04em", textTransform: "uppercase", color: "var(--text-muted)" }}>{it.brand}</div>
                  <button onClick={() => removeItem(it.id)} style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--text-faint)", padding: 0 }}><Trash2 size={15} /></button>
                </div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text)", lineHeight: 1.3, margin: "2px 0 8px", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{it.title}</div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <QuantityStepper size="sm" value={it.qty} max={it.stock || 99} onChange={(q) => setQty(it.id, q)} />
                  <span className="t-price" style={{ fontSize: 15, color: "var(--text)" }}>{money(it.price * it.qty)}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {cart.length > 0 && (
          <div style={{ padding: "18px 20px", borderTop: "1px solid var(--border)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span style={{ color: "var(--text-secondary)", fontSize: 14 }}>Subtotal</span>
              <span className="t-price" style={{ fontSize: 16, color: "var(--text)" }}>{money(cartSubtotal)}</span>
            </div>
            <div style={{ fontSize: 12.5, color: "var(--text-muted)", marginBottom: 14 }}>Shipping & taxes calculated at checkout</div>
            <Button variant="primary" size="lg" block onClick={goCheckout} iconRight={<ArrowRight size={18} />}>Checkout</Button>
            <button onClick={onClose} style={{ width: "100%", marginTop: 10, border: "none", background: "transparent", color: "var(--text-secondary)", fontSize: 14, fontWeight: 600, cursor: "pointer", padding: 6 }}>Continue shopping</button>
          </div>
        )}
      </aside>
    </>
  );
}
