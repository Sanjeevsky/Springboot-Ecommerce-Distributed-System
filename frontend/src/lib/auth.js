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
