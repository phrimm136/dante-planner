/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
// import { tanstackRouter } from '@tanstack/router-plugin/vite'
import { visualizer } from 'rollup-plugin-visualizer'
import path from 'path'
import fs from 'fs'
import type { Plugin } from 'vite'
import { hashStaticPlugin } from './vite-plugin-hash-static'

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
    hashStaticPlugin({ staticDir: path.resolve(__dirname, '../static') }),
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
  json: {
    namedExports: false,
    stringify: true,
  },
  publicDir: '../static',
  build: {
    rollupOptions: {
      output: {
        entryFileNames: 'a/[hash:12].js',
        chunkFileNames: 'a/[hash:12].js',
        assetFileNames: 'a/[hash:12][extname]',
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('node_modules/react-dom') || id.includes('node_modules/react/'))
            return 'react-vendor'
          if (id.includes('@tanstack/react-query') || id.includes('@tanstack/react-router'))
            return 'tanstack'
          if (id.includes('@radix-ui/'))
            return 'radix'
          if (id.includes('i18next') || id.includes('react-i18next') || id.includes('i18next-browser-languagedetector'))
            return 'i18n'
          if (id.includes('lucide-react'))
            return 'icons'
          if (id.includes('node_modules/zod'))
            return 'zod'
          if (id.includes('node_modules/sonner'))
            return 'sonner'
          if (id.includes('@tiptap/'))
            return 'tiptap'
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
