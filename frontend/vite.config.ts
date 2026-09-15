import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// The API runs on 8082 (Spring Boot). Proxying /api keeps the browser on one origin, so no CORS.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5180,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8082', changeOrigin: false },
    },
  },
  preview: {
    port: 5180,
    proxy: {
      '/api': { target: 'http://localhost:8082', changeOrigin: false },
    },
  },
})
