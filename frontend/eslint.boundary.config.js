// Boundary-only lint gate. Standalone by design: the main eslint.config.js
// extends strictTypeChecked without parserOptions.projectService and throws at
// load, so CI runs this config to keep the structural rules executing. No
// type-aware extends here — only the import-boundary rules.
//
// Flat-config semantics: for any file, the LAST matching object that defines
// `no-restricted-imports` fully replaces earlier ones (no merge). Every
// directory override below is therefore self-contained.
import reactHooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

// Public-API deep-import bans applied to every file. A page slice or shared
// concept is reached through its index.ts barrel, never a deep internal path.
// Exception: @/shared/noteEditor/** is deep-importable because the Tiptap
// editor component and its utils are deliberately kept OUT of the noteEditor
// barrel to preserve the entry-chunk-tiptap-free bundle invariant; consumers
// (the planner editor and the vendored tiptap-* kit) reach them by deep path.
const DEEP_PAGES = { group: ['@/pages/*/**'], message: 'Import a page slice via its public API (@/pages/<name>), not a deep internal path.' }
const DEEP_SHARED = { group: ['@/shared/*/**', '!@/shared/noteEditor/**'], message: 'Import a shared concept via its public API (@/shared/<name>), not a deep internal path.' }

// Sink pattern: a whole page slice (barrel + deep) is off-limits to importers
// that must never depend on feature code.
const SINK_PAGES = { group: ['@/pages/*', '@/pages/*/**'], message: 'This layer is a dependency sink: it must not import page slices.' }
const SINK_SHARED = { group: ['@/shared/*', '@/shared/*/**'], message: 'lib is the domain-free sink: it must not import shared concepts.' }

export default defineConfig([
  globalIgnores(['dist']),

  // Plugins registered (not enabled) so inline eslint-disable comments in source
  // referencing their rules resolve instead of erroring "rule not found".
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks, '@typescript-eslint': tseslint.plugin },
    languageOptions: { parser: tseslint.parser },
    linterOptions: { reportUnusedDisableDirectives: 'off' },
    rules: {
      'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED] }],
    },
  },

  // Sink layers: shared concepts and the component kit may be imported by
  // features but must never import a page slice.
  {
    files: ['src/shared/**/*.{ts,tsx}', 'src/components/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED, SINK_PAGES] }] },
  },

  // lib is the domain-free sink: no page slices, no shared concepts. (lib's
  // non-React nature is a convention; the lone `import type { CSSProperties }`
  // in lib/utils.ts is a type-only import with no runtime/bundle effect, so no
  // react-import ban is added here.)
  {
    files: ['src/lib/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': ['error', { patterns: [SINK_PAGES, SINK_SHARED] }] },
  },

  // App-shell composition roots: GlobalLayout wraps the routed tree and mounts
  // the post-login SyncChoiceDialog (a settings-slice export); RouteErrorComponent
  // renders page-level error UI. Both legitimately compose page slices via their
  // public API, like the router. They keep the deep-import bans but are released
  // from the components->pages sink. Must come after the components-sink block.
  {
    files: ['src/components/layout/GlobalLayout.tsx', 'src/components/feedback/RouteErrorComponent.tsx'],
    rules: { 'no-restricted-imports': ['error', { patterns: [DEEP_PAGES, DEEP_SHARED] }] },
  },

  // The router is the composition root: it lazy-imports each page's route
  // component by module path for code-splitting. Must stay last so it wins.
  {
    files: ['src/lib/router.tsx'],
    rules: { 'no-restricted-imports': 'off' },
  },
])
