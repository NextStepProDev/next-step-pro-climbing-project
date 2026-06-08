import { readFileSync } from 'fs'
import { resolve } from 'path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const version = (() => {
  try {
    return readFileSync(resolve(__dirname, '../VERSION'), 'utf-8').trim()
  } catch {
    return process.env.VITE_APP_VERSION ?? 'dev'
  }
})()

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    exclude: ['e2e/**', 'node_modules/**'],
  },
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  build: {
    rollupOptions: {
      output: {
        // Vite 8 bundles with Rolldown, which only accepts the function form of
        // manualChunks. Order matters: more specific packages are matched before
        // the generic react/* fallthrough (e.g. react-i18next -> i18n, not vendor).
        manualChunks: (id) => {
          if (!id.includes('node_modules')) return
          if (id.includes('@tanstack')) return 'query'
          if (id.includes('i18next')) return 'i18n'
          if (id.includes('lucide-react')) return 'icons'
          if (id.includes('date-fns')) return 'dates'
          if (
            id.includes('react-router') ||
            id.includes('react-dom') ||
            id.includes('/react/') ||
            id.includes('scheduler')
          ) {
            return 'vendor'
          }
        },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
