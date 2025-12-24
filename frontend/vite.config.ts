/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { TanStackRouterVite } from '@tanstack/router-plugin/vite'
import path from 'path'
import fs from 'fs'
import type { Plugin } from 'vite'

// Plugin to return 404 for missing static files (images, etc.)
function staticFile404Plugin(): Plugin {
  return {
    name: 'static-file-404',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url || ''
        // Check if request is for a static file (images, etc.)
        if (url.startsWith('/images/') && !url.includes('?')) {
          const filePath = path.resolve(__dirname, '../static', url.slice(1))
          if (!fs.existsSync(filePath)) {
            res.statusCode = 404
            res.end('Not found')
            return
          }
        }
        next()
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    staticFile404Plugin(),
    TanStackRouterVite({
      autoCodeSplitting: true,
    }),  // Must be BEFORE react() plugin
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler']
      }
    }),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@static': path.resolve(__dirname, '../static'),
    },
  },
  server: {
    fs: {
      allow: ['..'],
    },
  },
  publicDir: '../static',
  test: {
    // Test environment
    environment: 'jsdom',

    // jsdom environment options
    environmentOptions: {
      jsdom: {
        url: 'http://localhost',
        resources: 'usable',
        storageQuota: 10000000, // 10MB
      },
    },

    // Global test APIs (describe, it, expect, etc.)
    globals: true,

    // Setup file
    setupFiles: './vitest.setup.ts',

    // Coverage configuration
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.test.{ts,tsx}',
        'src/**/*.spec.{ts,tsx}',
        'src/main.tsx',
        'src/routeTree.gen.ts',
        'src/**/*.d.ts',
      ],
    },

    // Process pool for stability
    pool: 'forks',

    // Better mock management
    clearMocks: true,
    restoreMocks: true,
  },
})
