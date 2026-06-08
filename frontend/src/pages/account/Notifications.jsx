import React, { useState, useEffect } from "react";
import { Truck, Tag, Star, PackageCheck, ShieldCheck, CheckCheck } from "lucide-react";
import { Button, Badge } from "../../components/index.js";
import { notifications as notificationsService } from "../../lib/services.js";

const ICON_MAP = { Truck, Tag, Star, PackageCheck, ShieldCheck };

const TONE_COLOR = {
  info:    "var(--info)",
  sale:    "var(--sale)",
  accent:  "var(--accent)",
  success: "var(--success)",
  primary: "var(--primary)",
};
const TONE_BG = {
  info:    "var(--info-subtle)",
  sale:    "var(--sale-subtle)",
  accent:  "var(--accent-subtle)",
  success: "var(--success-subtle)",
  primary: "var(--primary-subtle)",
};

export default function Notifications() {
  const [items, setItems] = useState([]);

  useEffect(() => {
    notificationsService.list().then((list) => setItems(list));
  }, []);

  const markRead = (id) => {
    setItems((s) => s.map((n) => n.id === id ? { ...n, unread: false } : n));
    notificationsService.markRead(id).catch(() => {});
  };
  const markAllRead = () => {
    setItems((s) => {
      s.filter((n) => n.unread).forEach((n) => notificationsService.markRead(n.id).catch(() => {}));
      return s.map((n) => ({ ...n, unread: false }));
    });
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", marginBottom: 22, gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Notifications</h1>
          <p style={{ color: "var(--text-muted)", fontSize: 15 }}>Orders, price drops, and account activity.</p>
        </div>
        <Button variant="ghost" onClick={markAllRead} iconLeft={<CheckCheck size={17} />}>Mark all as read</Button>
      </div>

      <div style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", overflow: "hidden" }}>
        {items.map((n, i) => {
          const Icon = ICON_MAP[n.icon] || ShieldCheck;
          return (
            <div key={n.id} onClick={() => markRead(n.id)}
              style={{ display: "flex", gap: 14, padding: "18px 20px", borderBottom: i < items.length - 1 ? "1px solid var(--border)" : "none", cursor: "pointer", background: n.unread ? "var(--primary-subtle)" : "transparent", transition: "background var(--dur-fast)" }}>
              <span style={{ width: 40, height: 40, borderRadius: "var(--radius-md)", flex: "none", display: "inline-flex", alignItems: "center", justifyContent: "center", background: TONE_BG[n.tone], color: TONE_COLOR[n.tone] }}>
                <Icon size={20} />
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 15, fontWeight: 700, color: "var(--text)" }}>{n.title}</span>
                  {n.unread && <span style={{ width: 7, height: 7, borderRadius: 999, background: "var(--sale)", flex: "none" }} />}
                  <span style={{ marginLeft: "auto", fontSize: 12.5, color: "var(--text-faint)", flex: "none" }}>{n.time}</span>
                </div>
                <p style={{ fontSize: 14, color: "var(--text-secondary)", lineHeight: 1.45, marginTop: 3 }}>{n.body}</p>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
