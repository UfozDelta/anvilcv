import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // ponytail: build straight into Spring's static dir so one JAR serves API + UI.
  // Same origin => no CORS, no cross-site cookie. Drop this if the frontend ever
  // moves to Vercel; the CORS bean in SecurityConfig already covers that case.
  // must be absolute, not './' — BrowserRouter deep links like /applications/42
  // would otherwise resolve assets against the wrong directory.
  base: '/',
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
  },
})
