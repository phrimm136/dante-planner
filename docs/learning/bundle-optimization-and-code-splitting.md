# Bundle Optimization and Code Splitting

## Problem

The application had a 3-second blank screen before the loading skeleton appeared on page refresh. Investigation revealed:

1. **2639 tiny JS chunks** - Each `import()` for JSON files created a separate chunk
2. **360KB main bundle** (112KB gzip) - Too large for fast parsing
3. **Tiptap bundled in main** - 382KB editor library pulled into initial load via barrel imports
4. **No visual feedback** - Blank screen while JavaScript loaded

## Root Causes

### Dynamic Import Fragmentation

```typescript
// BAD: Creates a separate JS chunk for each JSON file
const module = await import(`@static/data/identity/${id}.json`)
```

Vite wraps each dynamic import in a small JS chunk for module resolution. With hundreds of JSON files, this created 2639 chunks.

### Barrel File Pollution

```typescript
// schemas/index.ts (barrel file)
export { NoteEditorSchemas } from './NoteEditorSchemas'
//        ↓
// NoteEditorSchemas.ts
import type { JSONContent } from '@tiptap/core'  // Pulls in 382KB!
```

Any file importing from `@/schemas` barrel would include Tiptap in its chunk, even if not using editor features.

### Missing Vendor Splitting

All third-party libraries were bundled together, preventing parallel loading.

## Solutions

### 1. Convert import() to fetch() for JSON

```typescript
// GOOD: JSON served as static asset, no JS chunk created
const response = await fetch(`/data/identity/${id}.json`)
const data: unknown = await response.json()
const result = IdentityDataSchema.safeParse(data)
```

**Result**: 2639 chunks → 47 chunks

### 2. Remove Heavy Exports from Barrel

```typescript
// schemas/index.ts
// Planner schemas - EXCLUDED from barrel to avoid bundling Tiptap
// Import directly: import { ... } from '@/schemas/PlannerSchemas'
```

**Result**: Tiptap now lazy-loads only for Planner pages

### 3. Manual Chunk Splitting

```typescript
// vite.config.ts
manualChunks: {
  'react-vendor': ['react', 'react-dom'],
  'tanstack': ['@tanstack/react-query', '@tanstack/react-router'],
  'radix': ['@radix-ui/react-dialog', '@radix-ui/react-dropdown-menu', ...],
  'i18n': ['i18next', 'react-i18next', 'i18next-browser-languagedetector'],
  'icons': ['lucide-react'],
  'zod': ['zod'],
  'sonner': ['sonner'],
  'tiptap': ['@tiptap/core', '@tiptap/react', '@tiptap/starter-kit', ...],
}
```

Vite adds `<link rel="modulepreload">` for these chunks, enabling parallel loading.

### 4. Inline Loading Spinner

```html
<div id="root">
  <div id="initial-loader">
    <div class="loader-spinner"></div>
  </div>
</div>
```

Shows immediately without waiting for JS. React replaces it on mount.

## Results

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Chunk count | 2639 | 47 | 98% reduction |
| Main bundle | 360KB (112KB gzip) | 272KB (87KB gzip) | 22% smaller |
| Tiptap | In main bundle | Lazy loaded | 382KB deferred |
| Visual feedback | 3s blank | Immediate spinner | Instant |

### Final Bundle Breakdown

| Chunk | Gzipped | Loading |
|-------|---------|---------|
| index.js (main) | 87KB | Initial |
| radix.js | 34KB | Parallel |
| tanstack.js | 34KB | Parallel |
| i18n.js | 17KB | Parallel |
| zod.js | 15KB | Parallel |
| sonner.js | 9KB | Parallel |
| react-vendor.js | 4KB | Parallel |
| icons.js | 4KB | Parallel |
| **Total Initial** | **~204KB** | |
| tiptap.js | 121KB | Lazy (Planner only) |

## Key Learnings

### 1. Dynamic imports for data files are antipatterns

Use `fetch()` for JSON data. Dynamic `import()` is for code splitting, not data loading.

### 2. Barrel files can cause hidden bundle bloat

Type-only imports still trigger module resolution. Heavy dependencies in barrel exports pollute all consumers.

### 3. Vendor splitting enables HTTP/2 parallelism

Without manual chunks, all vendors bundle together. With splitting, browsers download them in parallel.

### 4. Inline HTML beats React for initial feedback

For immediate visual feedback, put content directly in HTML. Don't wait for React to hydrate.

### 5. Bundle analyzer is essential

`rollup-plugin-visualizer` generates `stats.html` showing exactly what's in each chunk.

## Patterns to Follow

### Data Fetching in Hooks

```typescript
// Always use fetch() for JSON data
const response = await fetch(`/data/${type}/${id}.json`)
if (!response.ok) throw new Error(`Failed: ${response.status}`)
const data: unknown = await response.json()
return Schema.safeParse(data)
```

### Import Heavy Dependencies Directly

```typescript
// DON'T: Import from barrel if it includes heavy deps
import { PlannerSchema } from '@/schemas'

// DO: Import directly to avoid pulling in Tiptap
import { PlannerSchema } from '@/schemas/PlannerSchemas'
```

### Check Bundle Impact Before Adding Dependencies

```bash
yarn run vite build
# Check stats.html for bundle sizes
```

## References

- Vite Manual Chunks: https://vitejs.dev/guide/build.html#chunking-strategy
- Module Preload: https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/rel/modulepreload
- HTTP/2 Multiplexing: https://web.dev/performance-http2/
