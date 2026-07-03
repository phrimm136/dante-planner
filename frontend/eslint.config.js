import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import reactYouMightNotNeedAnEffect from 'eslint-plugin-react-you-might-not-need-an-effect'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

// Shared with eslint.boundary.config.js — see that file for full rationale.
const DEEP_PAGES = { group: ['@/pages/*/**'], message: 'Import a page slice via its public API (@/pages/<name>), not a deep internal path.' }
const DEEP_SHARED = { group: ['@/shared/*/**', '!@/shared/noteEditor/**'], message: 'Import a shared concept via its public API (@/shared/<name>), not a deep internal path.' }
const SINK_PAGES = { group: ['@/pages/*', '@/pages/*/**'], message: 'This layer is a dependency sink: it must not import page slices.' }
const SINK_SHARED = { group: ['@/shared/*', '@/shared/*/**'], message: 'lib is the domain-free sink: it must not import shared concepts.' }

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      reactYouMightNotNeedAnEffect.configs.recommended,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
  },

  // ── Structural import boundaries ──
  // Mirror of eslint.boundary.config.js (the standalone CI gate). Kept in sync
  // so an IDE using this config sees the same rules. The four layers:
  //   pages/<slice>  shared/<concept>  components (domain-free React)  lib (domain-free non-React)
  // Reach pages and shared concepts only through their public API (index.ts).
  // Sink direction: shared/components/lib may not import pages; lib may not
  // import shared. See the boundary config for the full rationale.
  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED] }],
    },
  },
  {
    files: ['src/shared/**/*.{ts,tsx}', 'src/components/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED, SINK_PAGES] }] },
  },
  {
    files: ['src/lib/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': ['error', { patterns: [SINK_PAGES, SINK_SHARED] }] },
  },
  {
    files: ['src/components/layout/GlobalLayout.tsx', 'src/components/feedback/RouteErrorComponent.tsx'],
    rules: { 'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED] }] },
  },
  {
    files: ['src/lib/router.tsx'],
    rules: { 'no-restricted-imports': 'off' },
  },
])
