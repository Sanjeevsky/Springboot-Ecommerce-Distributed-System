import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { CreditCard, ShieldCheck, Plus } from "lucide-react";
import { Button, Badge } from "../../components/index.js";
import { money } from "../../lib/format.js";
import { orders as ordersService, payment as paymentService } from "../../lib/services.js";

const STATUS_TONE = {
  SUCCESS:  { label: "Paid",     tone: "success" },
  PENDING:  { label: "Pending",  tone: "info" },
  FAILED:   { label: "Failed",   tone: "danger" },
  REFUNDED: { label: "Refunded", tone: "neutral" },
};

export default function Payments() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const list = await ordersService.list();
        const recent = list.slice(0, 12);
        const statuses = await Promise.all(
          recent.map((o) => paymentService.status(o.id).then((s) => (typeof s === "string" ? s : s?.status)).catch(() => null))
        );
        if (alive) setRows(recent.map((o, i) => ({ ...o, paymentStatus: statuses[i] })));
      } catch {
        if (alive) setRows([]);
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => { alive = false; };
  }, []);

  return (
    <div>
      <div style={{ marginBottom: 22 }}>
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Payment methods</h1>
        <p style={{ color: "var(--text-muted)", fontSize: 15 }}>Saved cards and your recent payment activity.</p>
      </div>

      {/* saved methods — demo platform pays via the payment-service mock gateway */}
      <div style={{ border: "1px dashed var(--border-strong)", borderRadius: "var(--radius-lg)", background: "var(--surface)", padding: "28px 24px", display: "flex", alignItems: "center", gap: 16, marginBottom: 28 }}>
        <span style={{ width: 44, height: 44, borderRadius: "var(--radius-md)", background: "var(--primary-subtle)", color: "var(--primary)", display: "inline-flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
          <CreditCard size={22} />
        </span>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: "var(--text)" }}>No saved payment methods</div>
          <div style={{ fontSize: 13.5, color: "var(--text-muted)", marginTop: 2 }}>
            Orders are charged through Trove's protected checkout. Card storage is coming soon.
          </div>
        </div>
        <Button variant="secondary" iconLeft={<Plus size={16} />} disabled>Add card</Button>
      </div>

      <h2 style={{ fontFamily: "var(--font-display)", fontSize: 19, fontWeight: 700, letterSpacing: "-0.02em", color: "var(--text)", marginBottom: 14 }}>Payment history</h2>

      <div style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", overflow: "hidden" }}>
        {loading && (
          <div style={{ padding: "28px 20px", textAlign: "center", color: "var(--text-muted)", fontSize: 14 }}>Loading payments…</div>
        )}
        {!loading && rows.length === 0 && (
          <div style={{ padding: "36px 20px", textAlign: "center", color: "var(--text-muted)", fontSize: 14 }}>
            No payments yet — they'll show up here after your first order.
          </div>
        )}
        {!loading && rows.map((o, i) => {
          const s = STATUS_TONE[o.paymentStatus] ?? { label: o.paymentStatus || "—", tone: "neutral" };
          return (
            <div key={o.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "16px 20px", borderBottom: i < rows.length - 1 ? "1px solid var(--border)" : "none" }}>
              <span style={{ width: 40, height: 40, borderRadius: "var(--radius-md)", flex: "none", display: "inline-flex", alignItems: "center", justifyContent: "center", background: "var(--success-subtle)", color: "var(--success)" }}>
                <ShieldCheck size={20} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <Link to={`/order/${o.id}`} style={{ fontSize: 14.5, fontWeight: 700, color: "var(--text)", textDecoration: "none" }}>
                  Order #{String(o.id).slice(0, 8)}
                </Link>
                <div style={{ fontSize: 13, color: "var(--text-muted)", marginTop: 2 }}>{o.date}</div>
              </div>
              <Badge tone={s.tone} size="sm">{s.label}</Badge>
              <span className="t-price" style={{ fontSize: 15, color: "var(--text)", minWidth: 90, textAlign: "right" }}>{money(o.total)}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
