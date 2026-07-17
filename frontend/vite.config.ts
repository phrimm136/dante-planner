/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import fs from 'fs'
import type { Plugin } from 'vite'
import { hashStaticPlugin } from './vite-plugin-hash-static'

const STATIC_ROOT = path.resolve(__dirname, '../static')
const STATIC_WHITELIST = ['images', 'data', 'i18n']
// Well-known files that must be served at stable root paths, outside the hashed pipeline
const STATIC_ROOT_FILES = ['sitemap.xml', 'robots.txt', 'favicon.ico', '_headers']

function serveWhitelistedStatic(): Plugin {
  return {
    name: 'serve-whitelisted-static',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url?.split('?')[0] ?? ''
        const segment = url.split('/')[1]
        if (segment && STATIC_WHITELIST.includes(segment)) {
          const filePath = path.join(STATIC_ROOT, url)
          if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
            return res.end(fs.readFileSync(filePath))
          }
          res.statusCode = 404
          res.end('Not found')
          return
        }
        next()
      })
    },
    async writeBundle() {
      const distRoot = path.resolve(__dirname, 'dist')
      for (const dir of STATIC_WHITELIST) {
        const src = path.join(STATIC_ROOT, dir)
        const dst = path.join(distRoot, dir)
        if (fs.existsSync(src)) {
          await fs.promises.cp(src, dst, {
            recursive: true,
            filter: (s) => !s.endsWith('.png'),
          })
        }
      }
      for (const file of STATIC_ROOT_FILES) {
        const src = path.join(STATIC_ROOT, file)
        if (fs.existsSync(src)) {
          await fs.promises.copyFile(src, path.join(distRoot, file))
        }
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
    serveWhitelistedStatic(),
    staticFile404Plugin(),
    hashStaticPlugin({ staticDir: path.resolve(__dirname, '../static') }),
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
    proxy: {
      // Proxy API requests to backend (nginx) for same-origin cookie handling
      '/api': {
        target: 'http://localhost',
        changeOrigin: true,
      },
    },
  },
  json: {
    namedExports: false,
    stringify: true,
  },
  publicDir: false,
  build: {
    rolldownOptions: {
      output: {
        entryFileNames: 'a/[hash:12].js',
        chunkFileNames: 'a/[hash:12].js',
        assetFileNames: 'a/[hash:12][extname]',
        // Vendor splitter. Rolldown evaluates groups in order, first match wins —
        // mirrors the old manualChunks if-chain (react-vendor must precede the
        // react-i18next/react-router matchers; the `react` trailing slash keeps it
        // from swallowing react-dom/react-i18next).
        codeSplitting: {
          groups: [
            { name: 'react-vendor', test: /node_modules\/(react-dom|react)\// },
            { name: 'tanstack', test: /node_modules\/@tanstack\/(react-query|react-router)/ },
            { name: 'radix', test: /node_modules\/@radix-ui\// },
            { name: 'i18n', test: /node_modules\/(i18next|react-i18next|i18next-browser-languagedetector)/ },
            { name: 'icons', test: /node_modules\/lucide-react/ },
            { name: 'zod', test: /node_modules\/zod/ },
            { name: 'sonner', test: /node_modules\/sonner/ },
            { name: 'tiptap', test: /node_modules\/@tiptap\// },
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
