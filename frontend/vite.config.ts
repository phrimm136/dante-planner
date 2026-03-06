/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
// import { tanstackRouter } from '@tanstack/router-plugin/vite'
import { visualizer } from 'rollup-plugin-visualizer'
import path from 'path'
import fs from 'fs'
import type { Plugin } from 'vite'

// Plugin to exclude scripts/ from publicDir output
function excludeScriptsFromPublic(): Plugin {
  return {
    name: 'exclude-scripts-from-public',
    writeBundle() {
      const scriptsDir = path.resolve(__dirname, 'dist/scripts')
      if (fs.existsSync(scriptsDir)) {
        fs.rmSync(scriptsDir, { recursive: true })
      }
    },
  }
}

// Plugin to return 404 for missing static files (images, data, i18n)
function staticFile404Plugin(): Plugin {
  return {
    name: 'static-file-404',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url ?? ''
        // Check if request is for a static file (images, data, i18n)
        const staticPrefixes = ['/images/', '/data/', '/i18n/']
        const isStaticRequest = staticPrefixes.some(prefix => url.startsWith(prefix))
        if (isStaticRequest && !url.includes('?')) {
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
    excludeScriptsFromPublic(),
    staticFile404Plugin(),
    // tanstackRouter plugin disabled - using programmatic routing in lib/router.tsx instead
    // tanstackRouter({
    //   autoCodeSplitting: true,
    // }),
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler']
      }
    }),
    tailwindcss(),
    visualizer({
      filename: 'stats.html',
      open: false,
      gzipSize: true,
    }),
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
    proxy: {
      // Proxy API requests to backend (nginx) for same-origin cookie handling
      '/api': {
        target: 'http://localhost',
        changeOrigin: true,
      },
    },
  },
  publicDir: '../static',
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Core React - rarely changes, cache long-term
          'react-vendor': ['react', 'react-dom'],
          // TanStack libraries - moderate change frequency
          'tanstack': ['@tanstack/react-query', '@tanstack/react-router'],
          // Radix UI primitives - rarely changes, large
          'radix': [
            '@radix-ui/react-dropdown-menu',
            '@radix-ui/react-dialog',
            '@radix-ui/react-tabs',
            '@radix-ui/react-tooltip',
            '@radix-ui/react-collapsible',
            '@radix-ui/react-popover',
            '@radix-ui/react-slot',
            '@radix-ui/react-label',
            '@radix-ui/react-scroll-area',
            '@radix-ui/react-separator',
            '@radix-ui/react-slider',
          ],
          // i18n - rarely changes
          'i18n': ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
          // Icons - large, rarely changes
          'icons': ['lucide-react'],
          // Zod - validation library, used across app
          'zod': ['zod'],
          // Sonner - toast notifications
          'sonner': ['sonner'],
          // Tiptap editor - only used in Planner pages
          'tiptap': [
            '@tiptap/core',
            '@tiptap/react',
            '@tiptap/starter-kit',
            '@tiptap/extension-image',
            '@tiptap/extension-link',
          ],
        },
      },
    },
  },
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
