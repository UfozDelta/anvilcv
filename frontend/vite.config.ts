import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { rmSync, cpSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

// Build lands in frontend/dist, which is what Vercel expects, and is then mirrored
// into Spring's static dir so a single JAR can serve API + UI on one origin.
// Both deployment paths stay working; neither needs dashboard config.
const SPRING_STATIC = resolve(__dirname, '../src/main/resources/static')

function copyToSpringStatic() {
  return {
    name: 'copy-to-spring-static',
    apply: 'build' as const,
    closeBundle() {
      const dist = resolve(__dirname, 'dist')
      if (!existsSync(dist)) return
      // wipe first: asset filenames are content-hashed, so stale ones would pile up
      rmSync(SPRING_STATIC, { recursive: true, force: true })
      cpSync(dist, SPRING_STATIC, { recursive: true })
      console.log(`copied dist -> ${SPRING_STATIC}`)
    },
  }
}

export default defineConfig({
  plugins: [react(), copyToSpringStatic()],
  // absolute, not './' — BrowserRouter deep links like /applications/42 would
  // otherwise resolve assets against the wrong directory
  base: '/',
  server: {
    port: 5173,
  },
})
