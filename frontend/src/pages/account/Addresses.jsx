import React, { useState, useEffect } from "react";
import { Home, Briefcase, Plus, X } from "lucide-react";
import { Button, Badge, Input, Select } from "../../components/index.js";
import { customer } from "../../lib/services.js";

const EMPTY_FORM = { home: "", streetLocality: "", landmark: "", city: "", state: "", zipCode: "", phone: "", country: "India" };

function AddressModal({ initial, onSave, onClose }) {
  const [form, setForm] = useState(initial || EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError("");
    try {
      await onSave(form);
      onClose();
    } catch (err) {
      setError(err.message || "Failed to save address.");
      setSaving(false);
    }
  };

  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 1100, display: "flex", alignItems: "center", justifyContent: "center", background: "var(--overlay)", backdropFilter: "blur(4px)" }}>
      <div style={{ background: "var(--surface)", borderRadius: "var(--radius-xl)", padding: 28, width: "100%", maxWidth: 480, boxShadow: "var(--shadow-xl)", position: "relative" }}>
        <button onClick={onClose} style={{ position: "absolute", top: 16, right: 16, border: "none", background: "transparent", cursor: "pointer", color: "var(--text-muted)" }}><X size={20} /></button>
        <h2 style={{ fontFamily: "var(--font-display)", fontSize: 20, fontWeight: 700, color: "var(--text)", marginBottom: 20 }}>
          {initial ? "Edit address" : "Add new address"}
        </h2>
        <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <label style={{ display: "flex", flexDirection: "column", gap: 6, gridColumn: "1/-1" }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Flat / House no.</span>
              <Input value={form.home} onChange={set("home")} placeholder="Flat 4B, Tower C" required />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6, gridColumn: "1/-1" }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Street / Locality</span>
              <Input value={form.streetLocality} onChange={set("streetLocality")} placeholder="MG Road, Koramangala" required />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>City</span>
              <Input value={form.city} onChange={set("city")} placeholder="Bangalore" required />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>State</span>
              <Input value={form.state} onChange={set("state")} placeholder="Karnataka" required />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>ZIP code</span>
              <Input value={form.zipCode} onChange={set("zipCode")} placeholder="560001" required />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Phone</span>
              <Input value={form.phone} onChange={set("phone")} placeholder="+91 98765 43210" />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Landmark</span>
              <Input value={form.landmark} onChange={set("landmark")} placeholder="Near Metro Station" />
            </label>
            <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Country</span>
              <Select value={form.country} onChange={set("country")}>
                <option value="India">India</option>
                <option value="United States">United States</option>
                <option value="United Kingdom">United Kingdom</option>
              </Select>
            </label>
          </div>
          {error && <div style={{ fontSize: 13, color: "var(--danger)", background: "var(--danger-subtle)", padding: "8px 12px", borderRadius: "var(--radius-md)" }}>{error}</div>}
          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 4 }}>
            <Button variant="ghost" type="button" onClick={onClose}>Cancel</Button>
            <Button variant="primary" type="submit" loading={saving}>Save address</Button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function Addresses() {
  const [addresses, setAddresses] = useState([]);
  const [modal, setModal] = useState(null); // null | "add" | { address }

  useEffect(() => {
    customer.addresses().then(setAddresses).catch(() => {});
  }, []);

  const reload = () => customer.addresses().then(setAddresses).catch(() => {});

  const handleAdd = async (form) => {
    await customer.addAddress(form);
    await reload();
  };

  const handleEdit = (addr) => async (form) => {
    await customer.updateAddress(addr.id, form);
    await reload();
  };

  const handleDelete = async (id) => {
    await customer.deleteAddress(id);
    setAddresses((a) => a.filter((x) => x.id !== id));
  };

  return (
    <div>
      {modal && (
        <AddressModal
          initial={typeof modal === "object" ? {
            home: modal.line1?.split(", ")[0] || "",
            streetLocality: modal.line1?.split(", ").slice(1).join(", ") || "",
            landmark: modal.line2 || "",
            city: modal.city || "",
            state: modal.state || "",
            zipCode: modal.zip || "",
            phone: modal.phone || "",
            country: "India",
          } : null}
          onSave={typeof modal === "object" ? handleEdit(modal) : handleAdd}
          onClose={() => setModal(null)}
        />
      )}

      <div style={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", marginBottom: 22, gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Addresses</h1>
          <p style={{ color: "var(--text-muted)", fontSize: 15 }}>Where your Trove orders go.</p>
        </div>
        <Button variant="primary" iconLeft={<Plus size={17} />} onClick={() => setModal("add")}>Add address</Button>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        {addresses.map((a, idx) => {
          const Icon = idx === 0 ? Home : Briefcase;
          return (
            <div key={a.id} style={{ border: `1px solid ${a.default ? "var(--primary-border)" : "var(--border)"}`, borderRadius: "var(--radius-lg)", background: a.default ? "var(--primary-subtle)" : "var(--surface)", padding: 20 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
                <Icon size={18} style={{ color: "var(--text-secondary)" }} />
                <span style={{ fontSize: 15, fontWeight: 700, color: "var(--text)" }}>{a.label}</span>
                {a.default && <Badge tone="primary" size="sm" style={{ marginLeft: "auto" }}>Default</Badge>}
              </div>
              <div style={{ fontSize: 14.5, color: "var(--text)", fontWeight: 600, marginBottom: 4 }}>{a.name}</div>
              <div style={{ fontSize: 14, color: "var(--text-secondary)", lineHeight: 1.5 }}>
                {a.line1}{a.line2 ? `, ${a.line2}` : ""}<br />
                {a.city}, {a.state} {a.zip}<br />
                {a.phone}
              </div>
              <div style={{ display: "flex", gap: 8, marginTop: 16, paddingTop: 16, borderTop: `1px solid ${a.default ? "var(--primary-border)" : "var(--border)"}` }}>
                <Button variant="secondary" size="sm" onClick={() => setModal(a)}>Edit</Button>
                {!a.default && (
                  <Button variant="ghost" size="sm" style={{ color: "var(--danger)", marginLeft: "auto" }} onClick={() => handleDelete(a.id)}>Remove</Button>
                )}
              </div>
            </div>
          );
        })}

        <button onClick={() => setModal("add")} style={{ border: "1.5px dashed var(--border-strong)", borderRadius: "var(--radius-lg)", background: "transparent", padding: 20, cursor: "pointer", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 10, minHeight: 180, color: "var(--text-muted)" }}>
          <Plus size={26} />
          <span style={{ fontSize: 14.5, fontWeight: 600 }}>Add a new address</span>
        </button>
      </div>
    </div>
  );
}
