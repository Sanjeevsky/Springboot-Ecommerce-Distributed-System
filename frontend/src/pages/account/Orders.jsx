import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Badge, Tabs } from "../../components/index.js";
import { orders as ordersService } from "../../lib/services.js";
import { useStore } from "../../store/StoreContext.jsx";
import { money } from "../../lib/format.js";

export default function Orders() {
  const navigate = useNavigate();
  const { addToCart } = useStore();
  const [filter, setFilter] = useState("all");
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    ordersService.list().then((list) => { setOrders(list); setLoading(false); });
  }, []);

  const shown = filter === "all" ? orders
    : filter === "progress" ? orders.filter((o) => o.statusTone === "info")
    : filter === "delivered" ? orders.filter((o) => o.statusTone === "success")
    : orders.filter((o) => o.statusTone === "danger");

  if (loading) {
    return (
      <div style={{ color: "var(--text-muted)", fontSize: 15, padding: "40px 0" }}>
        Loading orders…
      </div>
    );
  }

  return (
    <div>
      <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Your orders</h1>
      <p style={{ color: "var(--text-muted)", fontSize: 15, marginBottom: 22 }}>Track, return, or buy things again.</p>

      <div style={{ marginBottom: 22 }}>
        <Tabs value={filter} onChange={setFilter} items={[
          { id: "all",       label: "All",        count: orders.length },
          { id: "progress",  label: "In progress" },
          { id: "delivered", label: "Delivered" },
          { id: "cancelled", label: "Cancelled" },
        ]} />
      </div>

      {shown.length === 0 && (
        <div style={{ textAlign: "center", padding: "60px 20px", border: "1px dashed var(--border-strong)", borderRadius: "var(--radius-lg)", color: "var(--text-muted)" }}>
          <p style={{ fontWeight: 600, color: "var(--text)" }}>No orders yet</p>
          <p style={{ fontSize: 14, marginTop: 4 }}>When you place an order, it'll appear here.</p>
        </div>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {shown.map((o) => (
          <div key={o.id} style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", overflow: "hidden" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 20, padding: "16px 20px", background: "var(--surface-2)", borderBottom: "1px solid var(--border)", flexWrap: "wrap" }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--text-muted)" }}>Order placed</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text)" }}>{o.date}</div>
              </div>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--text-muted)" }}>Total</div>
                <div className="t-price" style={{ fontSize: 14, color: "var(--text)" }}>{money(o.total)}</div>
              </div>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--text-muted)" }}>Order #</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text)", fontFamily: "var(--font-mono)" }}>{o.id.slice(0, 8).toUpperCase()}</div>
              </div>
              <div style={{ marginLeft: "auto" }}>
                <Badge tone={o.statusTone} dot>{o.status}</Badge>
              </div>
            </div>

            <div style={{ padding: "8px 20px" }}>
              {o.items.map((it, i) => (
                <div key={it.id} style={{ display: "flex", gap: 14, padding: "14px 0", borderBottom: i < o.items.length - 1 ? "1px solid var(--border)" : "none", alignItems: "center" }}>
                  <div style={{ width: 64, height: 64, borderRadius: "var(--radius-md)", overflow: "hidden", flex: "none", background: "var(--surface-2)" }}>
                    <img src={it.image} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    {it.brand && <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.04em", textTransform: "uppercase", color: "var(--text-muted)" }}>{it.brand}</div>}
                    <div style={{ fontSize: 14.5, fontWeight: 600, color: "var(--text)", lineHeight: 1.3 }}>{it.title}</div>
                    <div style={{ fontSize: 13, color: "var(--text-muted)", marginTop: 2 }}>Qty {it.qty} · {money(it.price)}</div>
                  </div>
                  <div style={{ flex: "none" }}>
                    <Button variant="secondary" size="sm" onClick={() => addToCart({ id: it.productId, title: it.title, price: it.price, image: it.image, brand: it.brand }, 1)}>Buy it again</Button>
                  </div>
                </div>
              ))}
            </div>

            <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "14px 20px", borderTop: "1px solid var(--border)" }}>
              <span style={{ fontSize: 14, fontWeight: 600, color: o.statusTone === "info" ? "var(--info)" : "var(--text-secondary)" }}>{o.eta}</span>
              <div style={{ marginLeft: "auto", display: "flex", gap: 10 }}>
                {o.statusTone === "info"    && <Button variant="primary"   size="sm" onClick={() => navigate(`/order/${o.id}`)}>Track package</Button>}
                {o.statusTone === "success" && <Button variant="secondary" size="sm" onClick={() => navigate(`/order/${o.id}`)}>Return items</Button>}
                <Button variant="ghost" size="sm" onClick={() => navigate(`/order/${o.id}`)}>View details</Button>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
