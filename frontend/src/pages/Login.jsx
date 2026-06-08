import React, { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Mail, Lock, Eye, EyeOff } from "lucide-react";
import { Button, Input } from "../components/index.js";
import { Logo } from "../components/storefront/Logo.jsx";
import { auth } from "../lib/services.js";

export default function Login() {
  const navigate = useNavigate();
  const [mode, setMode] = useState("login"); // "login" | "register"
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const res = mode === "login"
        ? await auth.login(email, password)
        : await auth.signup({ email, password, name });
      if (res?.token) {
        localStorage.setItem("trove_token", res.token);
        localStorage.setItem("trove_user", JSON.stringify(res.user));
      }
      navigate("/account/orders");
    } catch (err) {
      setError(mode === "login" ? "Your email or password is incorrect. Please try again." : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 440, margin: "56px auto", padding: "0 24px 64px" }}>
      <div style={{ textAlign: "center", marginBottom: 32 }}>
        <Logo size={36} showWord />
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: 28, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginTop: 18 }}>
          {mode === "login" ? "Welcome back" : "Create your account"}
        </h1>
        <p style={{ color: "var(--text-muted)", fontSize: 15, marginTop: 6 }}>
          {mode === "login" ? "Sign in to your Trove account." : "Join Trove and start discovering."}
        </p>
      </div>

      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {mode === "register" && (
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Full name</span>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Maya Okafor" required />
          </label>
        )}

        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Email</span>
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" iconLeft={<Mail size={17} />} required />
        </label>

        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Password</span>
            {mode === "login" && <a href="#" style={{ fontSize: 13, color: "var(--primary)", fontWeight: 600 }}>Forgot?</a>}
          </div>
          <Input type={showPw ? "text" : "password"} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" iconLeft={<Lock size={17} />}
            iconRight={
              <button type="button" onClick={() => setShowPw((s) => !s)} style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--text-muted)", padding: 0, display: "inline-flex" }}>
                {showPw ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            } required />
        </label>

        {error && (
          <div style={{ fontSize: 13.5, color: "var(--danger)", background: "var(--danger-subtle)", padding: "10px 14px", borderRadius: "var(--radius-md)" }}>
            {error}
          </div>
        )}

        <Button type="submit" variant="primary" size="lg" block loading={loading}>
          {mode === "login" ? "Sign in" : "Create account"}
        </Button>
      </form>

      <div style={{ textAlign: "center", marginTop: 22, fontSize: 14, color: "var(--text-muted)" }}>
        {mode === "login" ? "New to Trove? " : "Already have an account? "}
        <button onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}
          style={{ border: "none", background: "transparent", color: "var(--primary)", fontWeight: 700, cursor: "pointer", fontSize: 14, fontFamily: "inherit" }}>
          {mode === "login" ? "Create an account" : "Sign in"}
        </button>
      </div>

      <div style={{ marginTop: 32, display: "flex", alignItems: "center", gap: 10 }}>
        <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
        <span style={{ fontSize: 12, color: "var(--text-faint)", fontWeight: 600 }}>OR CONTINUE AS GUEST</span>
        <div style={{ flex: 1, height: 1, background: "var(--border)" }} />
      </div>
      <div style={{ marginTop: 16 }}>
        <Button variant="secondary" size="lg" block onClick={() => navigate("/")}>Browse without signing in</Button>
      </div>
    </div>
  );
}
