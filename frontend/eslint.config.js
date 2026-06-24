import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import reactYouMightNotNeedAnEffect from 'eslint-plugin-react-you-might-not-need-an-effect'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

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

  // ── Page-slice import boundary (the only structural rule) ──
  // The app is organized as page slices under src/pages/<name> (plus the
  // legacy features/extraction slice). Reach a slice only through its public
  // API (src/pages/<name>/index.ts), never a deep internal path. Cross-page
  // reuse is allowed and expected — a component owned by one page is imported
  // by another via that page's public API. No directional or sibling bans:
  // the slices form a DAG by discipline, not by lint.
  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          group: ['@/pages/*/**', '@/features/*/**'],
          message: 'Import a page slice via its public API (@/pages/<name>), not a deep internal path.',
        }],
      }],
    },
  },
  // The router is the composition root: it lazy-imports each page's route
  // component by its module path for code-splitting, which is a deep import by
  // design. Allow deep @/pages imports here, but keep the deep-feature ban.
  {
    files: ['src/lib/router.tsx'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          group: ['@/features/*/**'],
          message: 'Import a feature via its public API (@/features/<name>), not a deep internal path.',
        }],
      }],
    },
  },
])
