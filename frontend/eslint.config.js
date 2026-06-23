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

  // ── Feature-first import boundaries (enforced by lint, not by convention) ──
  // Order matters: the global public-API rule is first; the stricter shared/
  // feature rules come AFTER so they override it for those files (flat config
  // resolves a repeated rule by "last match wins", and ban-all subsumes deep-ban).

  // (c) Public API only — reach a feature through its index, never an internal path.
  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          group: ['@/features/*/**'],
          message: 'Import a feature via its public API (@/features/<name>), not an internal path.',
        }],
      }],
    },
  },
  // (b) Unidirectional flow — shared layers must not depend on features.
  {
    files: ['src/components/**', 'src/hooks/**', 'src/lib/**', 'src/schemas/**', 'src/types/**', 'src/stores/**'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          group: ['@/features/*', '@/features/*/**'],
          message: 'Shared layers must not import features (dependency flows shared → features → app).',
        }],
      }],
    },
  },
  // (a) Feature isolation — no feature imports another; use relative paths within a feature.
  {
    files: ['src/features/**'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [{
          group: ['@/features/*', '@/features/*/**'],
          message: 'A feature must not import another feature; use relative imports internally and compose at the app/route layer.',
        }],
      }],
    },
  },
])
