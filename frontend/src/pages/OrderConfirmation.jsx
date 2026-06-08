import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { CheckCircle2, Package, Truck, RotateCcw } from "lucide-react";
import { Button } from "../components/index.js";
import { orders as ordersApi } from "../lib/services.js";

export default function OrderConfirmation() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);

  useEffect(() => {
    if (orderId) ordersApi.get(orderId).then(setOrder).catch(() => {});
  }, [orderId]);

  const userEmail = (() => { try { return JSON.parse(localStorage.getItem("trove_user") || "{}").email || "your email"; } catch { return "your email"; } })();
  const displayId = orderId ? orderId.slice(0, 8).toUpperCase() : "";
  const statusLabel = order?.status || "Processing";
  const total = order?.total;

  return (
    <div style={{ maxWidth: 640, margin: "60px auto", padding: "0 24px 64px", textAlign: "center" }}>
      <div style={{ width: 72, height: 72, borderRadius: 999, background: "var(--success-subtle)", display: "inline-flex", alignItems: "center", justifyContent: "center", marginBottom: 24 }}>
        <CheckCircle2 size={36} style={{ color: "var(--success)" }} />
      </div>

      <h1 style={{ fontFamily: "var(--font-display)", fontSize: 34, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", lineHeight: 1.1 }}>
        Order confirmed!
      </h1>
      <p style={{ color: "var(--text-secondary)", fontSize: 17, marginTop: 12, lineHeight: 1.5 }}>
        Thanks for your order. We'll send a confirmation to {userEmail}.
      </p>

      <div style={{ margin: "28px 0", padding: "18px 24px", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", display: "inline-flex", alignItems: "center", gap: 12 }}>
        <Package size={20} style={{ color: "var(--primary)" }} />
        <div style={{ textAlign: "left" }}>
          <div style={{ fontSize: 13, color: "var(--text-muted)", fontWeight: 600 }}>Order number</div>
          <div style={{ fontFamily: "var(--font-mono)", fontSize: 18, fontWeight: 700, color: "var(--text)" }}>{displayId}</div>
        </div>
        {total != null && (
          <div style={{ textAlign: "left", marginLeft: 16, paddingLeft: 16, borderLeft: "1px solid var(--border)" }}>
            <div style={{ fontSize: 13, color: "var(--text-muted)", fontWeight: 600 }}>Total</div>
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 18, fontWeight: 700, color: "var(--text)" }}>${total.toFixed(2)}</div>
          </div>
        )}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16, marginTop: 4, marginBottom: 36 }}>
        {[
          [Truck, "Status", statusLabel],
          [RotateCcw, "Free returns", "Within 30 days"],
          [CheckCircle2, "Payment", "Protected & secured"],
        ].map(([Ic, label, value]) => (
          <div key={label} style={{ padding: "16px 12px", border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)" }}>
            <Ic size={20} style={{ color: "var(--primary)", marginBottom: 8 }} />
            <div style={{ fontSize: 12, color: "var(--text-muted)", fontWeight: 600 }}>{label}</div>
            <div style={{ fontSize: 14, color: "var(--text)", fontWeight: 600, marginTop: 2 }}>{value}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", gap: 12, justifyContent: "center", flexWrap: "wrap" }}>
        <Button variant="primary" size="lg" onClick={() => window.location.href = "/account/orders"}>
          Track your order
        </Button>
        <Button variant="secondary" size="lg" onClick={() => window.location.href = "/"}>
          Continue shopping
        </Button>
      </div>
    </div>
  );
}
