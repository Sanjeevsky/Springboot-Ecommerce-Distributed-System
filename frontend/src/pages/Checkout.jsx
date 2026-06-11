import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Lock, ShieldCheck, Check, LogIn } from "lucide-react";
import { Button, Input, Select, Badge } from "../components/index.js";
import { useStore } from "../store/StoreContext.jsx";
import { money } from "../lib/format.js";
import { coupons, orders as ordersApi, customer, cart as cartApi } from "../lib/services.js";
import { isLoggedIn, currentUser } from "../lib/auth.js";

function Field({ label, half, children }) {
  return (
    <label style={{ display: "flex", flexDirection: "column", gap: 6, gridColumn: half ? "span 1" : "1 / -1" }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>{label}</span>
      {children}
    </label>
  );
}

function StepBubble({ n, active, done }) {
  return (
    <span style={{
      width: 28, height: 28, borderRadius: 999, flex: "none", display: "inline-flex", alignItems: "center", justifyContent: "center",
      fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 13,
      background: done ? "var(--primary)" : active ? "var(--primary-subtle)" : "var(--surface-2)",
      color: done ? "var(--on-primary)" : active ? "var(--primary)" : "var(--text-muted)",
      border: active && !done ? "1px solid var(--primary-border)" : "none",
    }}>
      {done ? <Check size={14} /> : n}
    </span>
  );
}

function Step({ n, title, active, done, children }) {
  return (
    <div style={{ borderBottom: "1px solid var(--border)", paddingBottom: 24, marginBottom: 24 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: active ? 18 : 0 }}>
        <StepBubble n={n} active={active} done={done} />
        <h3 style={{ fontFamily: "var(--font-display)", fontSize: 19, fontWeight: 700, letterSpacing: "-0.02em", color: active || done ? "var(--text)" : "var(--text-muted)" }}>{title}</h3>
      </div>
      {active && children}
    </div>
  );
}

const SHIPPING_OPTIONS = [
  { id: "standard", label: "Standard",  sub: "5–7 business days", price: 0 },
  { id: "express",  label: "Express",   sub: "2 business days",   price: 8 },
  { id: "next",     label: "Next day",  sub: "Order by 2pm",      price: 18 },
];

const EMPTY_ADDR = { name: "", phone: "", home: "", streetLocality: "", landmark: "", city: "", state: "", zipCode: "", country: "India" };

export default function Checkout() {
  const navigate = useNavigate();
  const { cart, cartSubtotal, clearCart } = useStore();
  const [ship, setShip] = useState("standard");
  const [couponCode, setCouponCode] = useState("");
  const [couponResult, setCouponResult] = useState(null);
  const [couponLoading, setCouponLoading] = useState(false);
  const [placing, setPlacing] = useState(false);
  const [addresses, setAddresses] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [placeError, setPlaceError] = useState("");

  // Inline address form state (shown when no saved addresses)
  const [addrForm, setAddrForm] = useState(EMPTY_ADDR);
  const [savingAddr, setSavingAddr] = useState(false);
  const [addrError, setAddrError] = useState("");

  const loggedIn = isLoggedIn();

  useEffect(() => {
    if (!loggedIn) return;
    customer.addresses().then((list) => {
      setAddresses(list);
      if (list.length > 0) setSelectedId(list[0].id);
    }).catch(() => {});
  }, [loggedIn]);

  const shippingCost = SHIPPING_OPTIONS.find((o) => o.id === ship)?.price ?? 0;
  const discount = couponResult?.valid ? couponResult.amount : 0;
  const tax = +((cartSubtotal - discount) * 0.08).toFixed(2);
  const total = cartSubtotal + shippingCost - discount + tax;

  const applyCode = async () => {
    setCouponLoading(true);
    const res = await coupons.validate(couponCode, cartSubtotal);
    setCouponResult(res);
    setCouponLoading(false);
  };

  const set = (k) => (e) => setAddrForm((f) => ({ ...f, [k]: e.target.value }));

  const saveAddress = async (e) => {
    e.preventDefault();
    setAddrError("");
    if (!addrForm.home || !addrForm.streetLocality || !addrForm.city || !addrForm.state || !addrForm.zipCode) {
      setAddrError("Please fill in all required fields.");
      return;
    }
    if (String(addrForm.zipCode).length < 4) {
      setAddrError("Please enter a valid ZIP / PIN code.");
      return;
    }
    setSavingAddr(true);
    try {
      await customer.addAddress({ ...addrForm, zipCode: Number(addrForm.zipCode) });
      const updated = await customer.addresses();
      setAddresses(updated);
      if (updated.length > 0) setSelectedId(updated[0].id);
    } catch (err) {
      setAddrError(err.message || "Failed to save address.");
    } finally {
      setSavingAddr(false);
    }
  };

  const placeOrder = async () => {
    setPlacing(true);
    setPlaceError("");
    try {
      await cartApi.clear().catch(() => {});
      await Promise.all(cart.map((it) => cartApi.add(it.id, it.qty)));
      const addressId = selectedId || addresses[0]?.id;
      if (!addressId) throw new Error("Please add a shipping address before placing your order.");
      const order = await ordersApi.place({
        addressId,
        couponCode: couponResult?.valid ? couponResult.code : undefined,
      });
      clearCart();
      navigate(`/order/${order.id}`);
    } catch (err) {
      setPlaceError(err.message || "Failed to place order. Please try again.");
      setPlacing(false);
    }
  };

  return (
    <div style={{ maxWidth: 1100, margin: "0 auto", padding: "24px 24px 0" }}>
      <Link to="/" style={{ display: "inline-flex", alignItems: "center", gap: 6, color: "var(--text-muted)", fontSize: 14, fontWeight: 600, marginBottom: 16 }}>
        <ArrowLeft size={16} /> Back to shopping
      </Link>
      <h1 style={{ fontFamily: "var(--font-display)", fontSize: 32, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 24 }}>Checkout</h1>

      <div style={{ display: "grid", gridTemplateColumns: "1.5fr 1fr", gap: 40, alignItems: "start" }}>
        <div>
          {/* step 1 — contact (pre-filled from localStorage, marked done) */}
          <Step n="1" title="Contact" done={loggedIn} active={!loggedIn}>
            <div style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap", padding: "14px 16px", borderRadius: "var(--radius-md)", border: "1px solid var(--border-strong)", background: "var(--surface)" }}>
              <div style={{ flex: 1, minWidth: 200 }}>
                <div style={{ fontSize: 14.5, fontWeight: 700, color: "var(--text)" }}>Checking out as a guest</div>
                <div style={{ fontSize: 13.5, color: "var(--text-muted)", marginTop: 2 }}>Sign in to use your saved addresses and track this order in your account.</div>
              </div>
              <Button variant="primary" iconLeft={<LogIn size={16} />} onClick={() => navigate("/login", { state: { from: "/checkout" } })}>
                Sign in
              </Button>
            </div>
          </Step>
          {loggedIn && (
            <div style={{ marginTop: -36, marginBottom: 24, marginLeft: 40, fontSize: 14, color: "var(--text-secondary)" }}>
              {currentUser().email || "Guest"}
            </div>
          )}

          {/* step 2 — shipping */}
          <Step n="2" title="Shipping address" active>
            {!loggedIn && (
              <div style={{ marginBottom: 16, fontSize: 13.5, color: "var(--text-secondary)", background: "var(--primary-subtle)", padding: "10px 14px", borderRadius: "var(--radius-md)" }}>
                Have an account?{" "}
                <Link to="/login" state={{ from: "/checkout" }} style={{ color: "var(--primary)", fontWeight: 700, textDecoration: "none" }}>Sign in</Link>
                {" "}and we'll fill this in from your saved addresses.
              </div>
            )}
            {addresses.length > 0 ? (
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {addresses.map((a) => (
                  <button key={a.id} type="button" onClick={() => setSelectedId(a.id)}
                    style={{ display: "flex", gap: 12, padding: "14px 16px", borderRadius: "var(--radius-md)", border: `1.5px solid ${selectedId === a.id ? "var(--primary)" : "var(--border-strong)"}`, background: selectedId === a.id ? "var(--primary-subtle)" : "var(--surface)", cursor: "pointer", textAlign: "left", width: "100%" }}>
                    <span style={{ width: 18, height: 18, borderRadius: 999, border: `2px solid ${selectedId === a.id ? "var(--primary)" : "var(--border-strong)"}`, flex: "none", marginTop: 2, display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
                      {selectedId === a.id && <span style={{ width: 8, height: 8, borderRadius: 999, background: "var(--primary)" }} />}
                    </span>
                    <div style={{ fontSize: 14, color: "var(--text)", lineHeight: 1.6 }}>
                      <div style={{ fontWeight: 700 }}>{a.name || a.label}</div>
                      <div style={{ color: "var(--text-secondary)" }}>
                        {a.line1}{a.line2 ? `, ${a.line2}` : ""}<br />
                        {a.city}, {a.state} {a.zip}
                        {a.phone && <><br />{a.phone}</>}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <form onSubmit={saveAddress} style={{ display: "flex", flexDirection: "column", gap: 0 }}>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                  <Field label="Full name" half>
                    <Input value={addrForm.name} onChange={set("name")} placeholder="Aisha Patel" />
                  </Field>
                  <Field label="Phone" half>
                    <Input value={addrForm.phone} onChange={set("phone")} placeholder="+91 98765 43210" />
                  </Field>
                  <Field label="Flat / House no." half>
                    <Input value={addrForm.home} onChange={set("home")} placeholder="Flat 4B, Tower C" required />
                  </Field>
                  <Field label="Landmark" half>
                    <Input value={addrForm.landmark} onChange={set("landmark")} placeholder="Near Metro Station" />
                  </Field>
                  <Field label="Street / Locality">
                    <Input value={addrForm.streetLocality} onChange={set("streetLocality")} placeholder="MG Road, Koramangala" required />
                  </Field>
                  <Field label="City" half>
                    <Input value={addrForm.city} onChange={set("city")} placeholder="Bangalore" required />
                  </Field>
                  <Field label="State" half>
                    <Input value={addrForm.state} onChange={set("state")} placeholder="Karnataka" required />
                  </Field>
                  <Field label="ZIP / PIN code" half>
                    <Input value={addrForm.zipCode} onChange={set("zipCode")} placeholder="560001" required />
                  </Field>
                  <Field label="Country" half>
                    <Select value={addrForm.country} onChange={set("country")}>
                      <option value="India">India</option>
                      <option value="United States">United States</option>
                      <option value="United Kingdom">United Kingdom</option>
                    </Select>
                  </Field>
                </div>
                {addrError && (
                  <div style={{ marginTop: 12, fontSize: 13, color: "var(--danger)", background: "var(--danger-subtle)", padding: "8px 12px", borderRadius: "var(--radius-md)" }}>
                    {addrError}
                  </div>
                )}
                <div style={{ marginTop: 16 }}>
                  <Button type="submit" variant="primary" loading={savingAddr}>Save address & continue</Button>
                </div>
              </form>
            )}

            <div style={{ marginTop: 20, display: "flex", flexDirection: "column", gap: 10 }}>
              {SHIPPING_OPTIONS.map(({ id, label, sub, price }) => (
                <button key={id} onClick={() => setShip(id)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "13px 16px", borderRadius: "var(--radius-md)", border: `1.5px solid ${ship === id ? "var(--primary)" : "var(--border-strong)"}`, background: ship === id ? "var(--primary-subtle)" : "var(--surface)", cursor: "pointer", textAlign: "left" }}>
                  <span style={{ width: 18, height: 18, borderRadius: 999, border: `2px solid ${ship === id ? "var(--primary)" : "var(--border-strong)"}`, flex: "none", display: "inline-flex", alignItems: "center", justifyContent: "center" }}>
                    {ship === id && <span style={{ width: 8, height: 8, borderRadius: 999, background: "var(--primary)" }} />}
                  </span>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14.5, fontWeight: 600, color: "var(--text)" }}>{label}</div>
                    <div style={{ fontSize: 13, color: "var(--text-muted)" }}>{sub}</div>
                  </div>
                  <span className="t-price" style={{ fontSize: 14, color: price === 0 ? "var(--success)" : "var(--text)" }}>
                    {price === 0 ? "Free" : money(price)}
                  </span>
                </button>
              ))}
            </div>
          </Step>

          <Step n="3" title="Payment"><div /></Step>
        </div>

        {/* order summary */}
        <aside style={{ position: "sticky", top: 138, border: "1px solid var(--border)", borderRadius: "var(--radius-xl)", background: "var(--surface)", overflow: "hidden" }}>
          <div style={{ padding: "20px 20px 0" }}>
            <h3 style={{ fontFamily: "var(--font-display)", fontSize: 18, fontWeight: 700, letterSpacing: "-0.02em", color: "var(--text)", marginBottom: 16 }}>Order summary</h3>
            <div style={{ display: "flex", flexDirection: "column", gap: 14, maxHeight: 220, overflowY: "auto", paddingBottom: 4 }}>
              {cart.map((it) => (
                <div key={it.id} style={{ display: "flex", gap: 12 }}>
                  <div style={{ position: "relative", width: 56, height: 56, borderRadius: "var(--radius-sm)", overflow: "hidden", flex: "none", background: "var(--surface-2)" }}>
                    <img src={it.image} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                    <span style={{ position: "absolute", top: -6, right: -6, width: 20, height: 20, borderRadius: 999, background: "var(--ink-700)", color: "#fff", fontSize: 11, fontFamily: "var(--font-mono)", fontWeight: 700, display: "flex", alignItems: "center", justifyContent: "center", border: "2px solid var(--surface)" }}>{it.qty}</span>
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text)", lineHeight: 1.3, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{it.title}</div>
                  </div>
                  <span className="t-price" style={{ fontSize: 13.5, color: "var(--text)" }}>{money(it.price * it.qty)}</span>
                </div>
              ))}
              {cart.length === 0 && <div style={{ color: "var(--text-muted)", fontSize: 14, textAlign: "center", padding: "20px 0" }}>Cart is empty</div>}
            </div>
          </div>

          {/* promo code */}
          <div style={{ padding: "16px 20px", borderTop: "1px solid var(--border)", marginTop: 16 }}>
            <div style={{ display: "flex", gap: 8 }}>
              <Input placeholder="Promo code" value={couponCode} onChange={(e) => setCouponCode(e.target.value)} wrapStyle={{ flex: 1 }} />
              <Button variant="secondary" onClick={applyCode} loading={couponLoading}>Apply</Button>
            </div>
            {couponResult?.valid && (
              <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 10 }}>
                <Badge tone="success" size="sm" dot>{couponResult.code} applied</Badge>
                <span style={{ fontSize: 12.5, color: "var(--text-muted)" }}>−{money(couponResult.amount)} off your order</span>
              </div>
            )}
            {couponResult && !couponResult.valid && (
              <div style={{ marginTop: 8, fontSize: 12.5, color: "var(--danger)" }}>Invalid promo code.</div>
            )}
          </div>

          {/* totals */}
          <div style={{ padding: "16px 20px", borderTop: "1px solid var(--border)", display: "flex", flexDirection: "column", gap: 10 }}>
            {[
              ["Subtotal",  money(cartSubtotal)],
              ["Shipping",  shippingCost === 0 ? "Free" : money(shippingCost)],
              ["Discount",  discount ? "−" + money(discount) : "—"],
              ["Tax",       money(tax)],
            ].map(([k, v]) => (
              <div key={k} style={{ display: "flex", justifyContent: "space-between", fontSize: 14 }}>
                <span style={{ color: "var(--text-secondary)" }}>{k}</span>
                <span className="t-price" style={{ fontSize: 13.5, color: k === "Discount" ? "var(--sale)" : "var(--text)" }}>{v}</span>
              </div>
            ))}
          </div>
          <div style={{ padding: "16px 20px", borderTop: "1px solid var(--border)", display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: "var(--text)" }}>Total</span>
            <span className="t-price" style={{ fontSize: 24, color: "var(--text)" }}>{money(total)}</span>
          </div>
          <div style={{ padding: "0 20px 20px" }}>
            <Button variant="primary" size="lg" block loading={placing} onClick={placeOrder} iconLeft={<Lock size={17} />}>
              Place order
            </Button>
            {placeError && (
              <div style={{ marginTop: 10, fontSize: 13, color: "var(--danger)", background: "var(--danger-subtle)", padding: "8px 12px", borderRadius: "var(--radius-md)" }}>
                {placeError}
              </div>
            )}
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 6, marginTop: 12, color: "var(--text-muted)", fontSize: 12.5 }}>
              <ShieldCheck size={14} /> Protected by Trove buyer guarantee
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
