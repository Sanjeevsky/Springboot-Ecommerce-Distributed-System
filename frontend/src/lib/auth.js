// Trove — session helpers around the JWT stored by login/signup.

export function isLoggedIn() {
  return !!localStorage.getItem("trove_token");
}

export function currentUser() {
  try { return JSON.parse(localStorage.getItem("trove_user") || "{}"); } catch { return {}; }
}

export function logout() {
  localStorage.removeItem("trove_token");
  localStorage.removeItem("trove_user");
}

// Role comes from the login response; older sessions fall back to the JWT's role claim.
export function isAdmin() {
  const stored = currentUser().role;
  if (stored) return stored === "ADMIN";
  const token = localStorage.getItem("trove_token");
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.role === "ADMIN";
  } catch {
    return false;
  }
}
