import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Lock, LogOut, Moon } from "lucide-react";
import { Button, Input, Avatar } from "../../components/index.js";
import { Switch } from "../../components/forms/Switch.jsx";
import { useTheme } from "../../store/ThemeContext.jsx";
import { auth as authService } from "../../lib/services.js";
import { currentUser, logout } from "../../lib/auth.js";

function Card({ title, sub, children }) {
  return (
    <section style={{ border: "1px solid var(--border)", borderRadius: "var(--radius-lg)", background: "var(--surface)", padding: "22px 24px", marginBottom: 20 }}>
      <h2 style={{ fontFamily: "var(--font-display)", fontSize: 18, fontWeight: 700, letterSpacing: "-0.02em", color: "var(--text)" }}>{title}</h2>
      {sub && <p style={{ color: "var(--text-muted)", fontSize: 13.5, marginTop: 4 }}>{sub}</p>}
      <div style={{ marginTop: 16 }}>{children}</div>
    </section>
  );
}

export default function Settings() {
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();
  const user = currentUser();
  const name = user.name || user.email?.split("@")[0] || "Account";

  const [oldPw, setOldPw] = useState("");
  const [newPw, setNewPw] = useState("");
  const [confirmPw, setConfirmPw] = useState("");
  const [saving, setSaving] = useState(false);
  const [pwError, setPwError] = useState("");
  const [pwDone, setPwDone] = useState(false);

  const changePassword = async (e) => {
    e.preventDefault();
    setPwError("");
    setPwDone(false);
    if (newPw.length < 6) { setPwError("New password must be at least 6 characters."); return; }
    if (newPw !== confirmPw) { setPwError("New passwords don't match."); return; }
    setSaving(true);
    try {
      await authService.updatePassword(user.email, oldPw, newPw);
      setPwDone(true);
      setOldPw(""); setNewPw(""); setConfirmPw("");
    } catch (err) {
      setPwError(err?.status === 401 || err?.status === 400
        ? "Your current password is incorrect."
        : "Couldn't update your password. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const signOut = () => {
    logout();
    navigate("/");
  };

  return (
    <div>
      <div style={{ marginBottom: 22 }}>
        <h1 style={{ fontFamily: "var(--font-display)", fontSize: 30, fontWeight: 800, letterSpacing: "-0.03em", color: "var(--text)", marginBottom: 6 }}>Settings</h1>
        <p style={{ color: "var(--text-muted)", fontSize: 15 }}>Your profile, security, and preferences.</p>
      </div>

      <Card title="Profile">
        <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
          <Avatar name={name} size="lg" />
          <div>
            <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text)" }}>{name}</div>
            <div style={{ fontSize: 14, color: "var(--text-muted)", marginTop: 2 }}>{user.email}</div>
          </div>
        </div>
      </Card>

      <Card title="Change password" sub="Use at least 6 characters.">
        <form onSubmit={changePassword} style={{ display: "flex", flexDirection: "column", gap: 14, maxWidth: 420 }}>
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Current password</span>
            <Input type="password" value={oldPw} onChange={(e) => setOldPw(e.target.value)} iconLeft={<Lock size={16} />} required />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>New password</span>
            <Input type="password" value={newPw} onChange={(e) => setNewPw(e.target.value)} iconLeft={<Lock size={16} />} required />
          </label>
          <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>Confirm new password</span>
            <Input type="password" value={confirmPw} onChange={(e) => setConfirmPw(e.target.value)} iconLeft={<Lock size={16} />} required />
          </label>
          {pwError && (
            <div style={{ fontSize: 13, color: "var(--danger)", background: "var(--danger-subtle)", padding: "8px 12px", borderRadius: "var(--radius-md)" }}>{pwError}</div>
          )}
          {pwDone && (
            <div style={{ fontSize: 13, color: "var(--success)", background: "var(--success-subtle)", padding: "8px 12px", borderRadius: "var(--radius-md)" }}>Password updated.</div>
          )}
          <div><Button type="submit" variant="primary" loading={saving}>Update password</Button></div>
        </form>
      </Card>

      <Card title="Appearance">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", maxWidth: 420 }}>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 10, fontSize: 14.5, color: "var(--text)" }}>
            <Moon size={17} /> Dark mode
          </span>
          <Switch checked={theme === "dark"} onChange={toggle} />
        </div>
      </Card>

      <Card title="Session">
        <Button variant="secondary" iconLeft={<LogOut size={16} />} onClick={signOut}>Sign out</Button>
      </Card>
    </div>
  );
}
