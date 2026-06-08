import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Mail, Lock, Eye, EyeOff, User, CheckCircle2 } from "lucide-react";
import { Button, Input } from "../components/index.js";
import { Logo } from "../components/storefront/Logo.jsx";
import { auth } from "../lib/services.js";

const PERKS = [
  "Free 30-day returns on every order",
  "Track orders and save addresses",
  "Wishlist and price-drop alerts",
];

export default function Signup() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const pwMatch = confirm === "" || password === confirm;
  const pwStrong = password.length >= 6;

  const submit = async (e) => {
    e.preventDefault();
    if (!pwMatch) { setError("Passwords don't match."); return; }
    if (!pwStrong) { setError("Password must be at least 6 characters."); return; }
    setError("");
    setLoading(true);
    try {
      const res = await auth.signup({ email, password, name });
      if (res?.token) {
        localStorage.setItem("trove_token", res.token);
        localStorage.setItem("trove_user", JSON.stringify(res.user));
      }
      navigate("/account/orders");
    } catch (err) {
      const msg = err?.message || "";
      if (msg.includes("already") || msg.includes("exists")) {
        setError("An account with this email already exists. Try signing in.");
      } else if (msg.includes("6 char") || msg.includes("password")) {
        setError("Password must be at least 6 characters.");
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  const eyeBtn = (show, toggle) => (
    <button type="button" onClick={toggle} style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--text-muted)", padding: 0, display: "inline-flex" }}>
      {show ? <EyeOff size={16} /> : <Eye size={16} />}
    </button>
  );

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", minHeight: "calc(100vh - 120px)" }}>
      {/* ── Left panel: form ── */}
      <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", padding: "56px clamp(32px, 6vw, 80px)", maxWidth: 540, margin: "0 auto", width: "100%" }}>
        <div style={{ marginBottom: 32 }}>
          <Logo size={32} showWord />
          <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginTop: 20, marginBottom: 6 }}>
            Create your account
          </h1>
          <p style={{ color: "var(--text-muted)", fontSize: 15 }}>
            Already have one?{" "}
            <Link to="/login" style={{ color: "var(--primary)", fontWeight: 700, textDecoration: "none" }}>Sign in</Link>
          </p>
        </div>

        <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Full name</span>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Aisha Patel"
              iconLeft={<User size={17} />}
              required
            />
          </label>

          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Email address</span>
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              iconLeft={<Mail size={17} />}
              required
            />
          </label>

          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Password</span>
            <Input
              type={showPw ? "text" : "password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 6 characters"
              iconLeft={<Lock size={17} />}
              iconRight={eyeBtn(showPw, () => setShowPw((s) => !s))}
              required
            />
            {password.length > 0 && (
              <div style={{ display: "flex", gap: 6, marginTop: 2 }}>
                {[1, 2, 3].map((i) => (
                  <div key={i} style={{ flex: 1, height: 3, borderRadius: 99, background: password.length >= i * 4 ? (password.length >= 10 ? "var(--success)" : "var(--warning)") : "var(--border)" }} />
                ))}
              </div>
            )}
          </label>

          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Confirm password</span>
            <Input
              type={showConfirm ? "text" : "password"}
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              placeholder="Repeat password"
              iconLeft={<Lock size={17} />}
              iconRight={eyeBtn(showConfirm, () => setShowConfirm((s) => !s))}
              style={!pwMatch ? { borderColor: "var(--danger)" } : {}}
              required
            />
            {!pwMatch && (
              <span style={{ fontSize: 12, color: "var(--danger)", fontWeight: 500 }}>Passwords don't match</span>
            )}
          </label>

          {error && (
            <div style={{ fontSize: 13.5, color: "var(--danger)", background: "var(--danger-subtle)", padding: "10px 14px", borderRadius: "var(--radius-md)", lineHeight: 1.4 }}>
              {error}
            </div>
          )}

          <Button type="submit" variant="primary" size="lg" block loading={loading} style={{ marginTop: 4 }}>
            Create account
          </Button>

          <p style={{ fontSize: 12, color: "var(--text-faint)", textAlign: "center", lineHeight: 1.5 }}>
            By creating an account you agree to Trove's{" "}
            <a href="#" style={{ color: "var(--text-muted)" }}>Terms of Service</a> and{" "}
            <a href="#" style={{ color: "var(--text-muted)" }}>Privacy Policy</a>.
          </p>
        </form>

        <div style={{ marginTop: 28, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
          <span style={{ fontSize: 12, color: "var(--text-faint)", fontWeight: 600 }}>OR</span>
          <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
        </div>
        <div style={{ marginTop: 16 }}>
          <Button variant="secondary" size="lg" block onClick={() => navigate("/")}>Browse without signing in</Button>
        </div>
      </div>

      {/* ── Right panel: perks ── */}
      <div style={{ background: "var(--primary)", display: "flex", flexDirection: "column", justifyContent: "center", padding: "56px clamp(32px, 6vw, 80px)" }}>
        <div style={{ color: "rgba(255,255,255,0.7)", fontSize: 12, fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase", marginBottom: 16 }}>
          Why join Trove?
        </div>
        <h2 style={{ fontFamily: "var(--font-display)", fontSize: "clamp(26px, 3vw, 38px)", fontWeight: 800, letterSpacing: "-0.03em", color: "#fff", lineHeight: 1.1, marginBottom: 32 }}>
          Shop smarter, every time.
        </h2>

        <div style={{ display: "flex", flexDirection: "column", gap: 20, marginBottom: 40 }}>
          {PERKS.map((perk) => (
            <div key={perk} style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <div style={{ width: 32, height: 32, borderRadius: 999, background: "rgba(255,255,255,0.15)", display: "inline-flex", alignItems: "center", justifyContent: "center", flex: "none" }}>
                <CheckCircle2 size={18} style={{ color: "#fff" }} />
              </div>
              <span style={{ color: "rgba(255,255,255,0.9)", fontSize: 16, fontWeight: 500 }}>{perk}</span>
            </div>
          ))}
        </div>

        <div style={{ borderRadius: "var(--radius-xl)", overflow: "hidden", height: 260, position: "relative" }}>
          <img
            src="https://images.unsplash.com/photo-1483985988355-763728e1935b?auto=format&fit=crop&w=700&q=72"
            alt=""
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
          />
          <div style={{ position: "absolute", inset: 0, background: "linear-gradient(to top, rgba(0,0,0,0.4) 0%, transparent 60%)" }} />
          <div style={{ position: "absolute", bottom: 16, left: 16, right: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "rgba(255,255,255,0.8)", letterSpacing: "0.04em" }}>TRENDING NOW</div>
            <div style={{ fontFamily: "var(--font-display)", fontSize: 18, fontWeight: 700, color: "#fff", marginTop: 2 }}>39 products · 10 categories</div>
          </div>
        </div>
      </div>
    </div>
  );
}
