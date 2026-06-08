import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Trove frontend — Vite config.
// The dev server proxies /api → the Spring Cloud Gateway (default :8081),
// so the browser never hits CORS during local development.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: process.env.VITE_GATEWAY_URL || "http://localhost:8081",
        changeOrigin: true,
        // strip the /api prefix: /api/catalog-service/... -> /catalog-service/...
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
