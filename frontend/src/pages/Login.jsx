import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Mail, Lock, Eye, EyeOff } from "lucide-react";
import { Button, Input } from "../components/index.js";
import { Logo } from "../components/storefront/Logo.jsx";
import { auth } from "../lib/services.js";

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = await auth.login(email, password);
      if (res?.token) {
        localStorage.setItem("trove_token", res.token);
        localStorage.setItem("trove_user", JSON.stringify(res.user));
      }
      navigate("/account/orders");
    } catch {
      setError("Your email or password is incorrect. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 440, margin: "56px auto", padding: "0 24px 64px" }}>
      <div style={{ textAlign: "center", marginBottom: 32 }}>
        <Logo size={36} showWord />
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: 28, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginTop: 18 }}>
          Welcome back
        </h1>
        <p style={{ color: "var(--text-muted)", fontSize: 15, marginTop: 6 }}>
          New to Trove?{" "}
          <Link to="/signup" style={{ color: "var(--primary)", fontWeight: 700, textDecoration: "none" }}>Create an account</Link>
        </p>
      </div>

      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Email</span>
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" iconLeft={<Mail size={17} />} required />
        </label>

        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Password</span>
            <a href="#" style={{ fontSize: 13, color: "var(--primary)", fontWeight: 600, textDecoration: "none" }}>Forgot?</a>
          </div>
          <Input
            type={showPw ? "text" : "password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            iconLeft={<Lock size={17} />}
            iconRight={
              <button type="button" onClick={() => setShowPw((s) => !s)} style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--text-muted)", padding: 0, display: "inline-flex" }}>
                {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            }
            required
          />
        </label>

        {error && (
          <div style={{ fontSize: 13.5, color: "var(--danger)", background: "var(--danger-subtle)", padding: "10px 14px", borderRadius: "var(--radius-md)" }}>
            {error}
          </div>
        )}

        <Button type="submit" variant="primary" size="lg" block loading={loading}>
          Sign in
        </Button>
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
  );
}
