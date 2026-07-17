/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      // 'script' emits /registerSW.js as its own file — an inline registration
      // snippet would be blocked by the CSP (script-src 'self').
      injectRegister: 'script',
      manifest: {
        name: 'finyo',
        short_name: 'finyo',
        description: 'Personal finance planner',
        display: 'standalone',
        start_url: '/',
        // zinc tokens from src/index.css: light background / dark primary
        background_color: '#ffffff',
        theme_color: '#18181b',
        icons: [
          { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: '/maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,webmanifest}'],
        // config.js is rewritten at container start (docker/40-runtime-config.sh);
        // both files must always come from the network, never the precache.
        globIgnores: ['config.js', 'theme-init.js'],
        navigateFallbackDenylist: [/^\/api\//, /^\/auth\//],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    // 5173 is registered in the Keycloak realm redirect URIs and the API CORS config
    port: 5173,
    proxy: {
      '/api': {
        // finyo-be from docker compose (host port 8082)
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: 'coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/vite-env.d.ts', 'src/test/**'],
    },
  },
})
