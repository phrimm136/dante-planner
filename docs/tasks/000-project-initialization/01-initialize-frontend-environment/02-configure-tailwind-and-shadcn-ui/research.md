# Research: Configure Tailwind CSS and shadcn/ui

## Overview of Codebase

### Current Frontend Setup
The LimbusPlanner frontend project is a fresh Vite + React + TypeScript application initialized with the following stack:

**Technologies:**
- **Vite 7.2.2** - Build tool and dev server
- **React 19.2.0** - UI library (latest version)
- **TypeScript 5.9.3** - Type safety
- **ESLint 9.39.1** - Code linting

**Key Observations:**
- The project uses modern React 19 with the latest features
- TypeScript is configured with strict mode enabled
- `@types/node` is already installed (v24.10.0) - required for path resolution
- No CSS framework is currently installed (uses vanilla CSS)
- Standard Vite project structure with `src/` directory

### Tailwind CSS v4 + Vite (2025 Best Practices)

Based on official documentation research, Tailwind CSS v4 introduces a **simplified installation process** for Vite:

**Key Changes in v4:**
1. **No `tailwind.config.js` required** - Configuration is optional
2. **No PostCSS configuration needed** - Handled by `@tailwindcss/vite` plugin
3. **Simpler CSS imports** - Just `@import "tailwindcss";` in your CSS file
4. **Plugin-based setup** - Uses `@tailwindcss/vite` for seamless integration

**Installation Pattern:**
```bash
npm install tailwindcss @tailwindcss/vite
```

**Configuration Pattern:**
```typescript
// vite.config.ts
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
})
```

**CSS Import Pattern:**
```css
/* src/index.css */
@import "tailwindcss";
```

### shadcn/ui Integration

shadcn/ui is a **component library** that provides:
- Pre-built, accessible React components
- Built on top of Radix UI primitives
- Styled with Tailwind CSS
- Full TypeScript support
- Copy/paste approach (components added to your codebase, not installed as dependency)

**Requirements:**
1. Tailwind CSS must be installed first
2. Path aliases must be configured (`@/*` -> `./src/*`)
3. TypeScript configuration for imports
4. CLI tool for adding components

**Installation Pattern:**
```bash
pnpm dlx shadcn@latest init
pnpm dlx shadcn@latest add button
```

**Component Usage Pattern:**
```typescript
import { Button } from "@/components/ui/button"
```

## Codebase Structure

### Current Structure
```
frontend/
├── vite.config.ts          # Vite configuration (needs Tailwind plugin)
├── package.json            # Dependencies
├── tsconfig.json           # Root TS config (needs path aliases)
├── tsconfig.app.json       # App TS config (needs path aliases)
├── tsconfig.node.json      # Node TS config
├── index.html              # Entry HTML
├── src/
│   ├── main.tsx           # App entry point
│   ├── App.tsx            # Root component
│   ├── App.css            # Component styles (can be replaced)
│   ├── index.css          # Global styles (will add Tailwind import here)
│   └── assets/            # Static assets
├── public/                # Public static files
└── node_modules/          # Dependencies
```

### Target Structure (After Configuration)
```
frontend/
├── vite.config.ts          # ✅ Add Tailwind plugin + path alias
├── tailwind.config.ts      # 🆕 Optional (for customization)
├── postcss.config.js       # ❌ NOT needed for v4
├── tsconfig.json           # ✅ Add baseUrl and paths
├── tsconfig.app.json       # ✅ Add baseUrl and paths
├── package.json            # ✅ Add Tailwind + shadcn dependencies
├── src/
│   ├── index.css          # ✅ Replace with Tailwind imports
│   ├── main.tsx
│   ├── App.tsx            # ✅ Test with Button component
│   └── components/
│       └── ui/            # 🆕 shadcn components folder
│           └── button.tsx # 🆕 Example: Button component
├── components.json        # 🆕 shadcn configuration file
└── lib/
    └── utils.ts           # 🆕 shadcn utility functions
```

### Path Alias Configuration

**Why needed:**
- shadcn/ui uses `@/` prefix for imports
- Cleaner imports: `@/components/ui/button` vs `../../../components/ui/button`
- Better maintainability and refactoring

**Configuration locations:**
1. `tsconfig.json` - TypeScript type checking
2. `tsconfig.app.json` - App-specific TS config
3. `vite.config.ts` - Runtime module resolution

**Pattern:**
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

## Gotchas and Pitfalls

### 1. Tailwind CSS Version Confusion

**Issue:** Many online guides reference Tailwind v3 setup, which requires different configuration.

**v3 (Old):**
- Requires `tailwind.config.js`
- Requires `postcss.config.js`
- Uses `@tailwind base; @tailwind components; @tailwind utilities;`

**v4 (Current - 2025):**
- Optional config files
- No PostCSS config needed
- Uses `@import "tailwindcss";`

**Mitigation:** Always use official Tailwind CSS documentation and specify v4 setup with `@tailwindcss/vite` plugin.

### 2. TypeScript Path Alias Issues

**Issue:** Path aliases must be configured in **multiple files** or imports will fail.

**Required files:**
- `tsconfig.json` - For TypeScript compiler
- `tsconfig.app.json` - For application code (Vite splits config)
- `vite.config.ts` - For Vite's module resolution at runtime

**Common error if misconfigured:**
```
Cannot find module '@/components/ui/button' or its corresponding type declarations
```

**Mitigation:** Update all three files with consistent path alias configuration.

### 3. shadcn/ui CLI Compatibility

**Issue:** shadcn uses `pnpm dlx` in examples, but we're using `yarn`.

**Yarn equivalent:**
```bash
# Instead of: pnpm dlx shadcn@latest init
yarn dlx shadcn@latest init

# Or use npx:
npx shadcn@latest init
```

**Mitigation:** Use `yarn dlx` or `npx` as alternatives to `pnpm dlx`.

### 4. CSS Import Location

**Issue:** Tailwind must be imported in a CSS file that's loaded by the app.

**Current setup:**
- `src/index.css` is imported in `src/main.tsx`
- This is the correct file to add `@import "tailwindcss";`

**Common mistake:** Creating a new CSS file but forgetting to import it in the app.

**Mitigation:** Use existing `src/index.css` file and verify it's imported in `main.tsx`.

### 5. React 19 Compatibility

**Issue:** The project uses React 19.2.0 (latest), which is very recent.

**Compatibility check:**
- ✅ Tailwind CSS v4 - Fully compatible
- ✅ shadcn/ui - Compatible (uses Radix UI which supports React 19)
- ⚠️ Some older component libraries may not support React 19 yet

**Mitigation:** Stick with shadcn/ui and Tailwind CSS as they are actively maintained and support React 19.

### 6. Vite Config TypeScript Import

**Issue:** Using `path.resolve(__dirname, "./src")` in `vite.config.ts` requires Node types.

**Current status:**
- ✅ `@types/node` is already installed (v24.10.0)

**Required import:**
```typescript
import path from "path"
```

**Mitigation:** Ensure `@types/node` is in `devDependencies` (already present).

### 7. Components.json Configuration

**Issue:** shadcn CLI creates a `components.json` file for configuration.

**What it controls:**
- Component installation path
- Utility function path
- TypeScript usage
- Styling preferences
- Alias configuration

**Best practice:** Let the CLI create this file during `shadcn init` and don't manually edit unless necessary.

### 8. Tailwind IntelliSense

**Issue:** VS Code Tailwind IntelliSense may not work without proper configuration.

**Requirements:**
- Install "Tailwind CSS IntelliSense" VS Code extension
- Ensure Tailwind is properly configured
- May need to reload VS Code after installation

**Mitigation:** Include in testing guidelines to verify IntelliSense works.

## Installation Summary

### Prerequisites (Already Met)
- ✅ Vite project initialized
- ✅ React + TypeScript configured
- ✅ `@types/node` installed
- ✅ Standard project structure

### Required Steps
1. Install Tailwind CSS v4 with Vite plugin
2. Configure Tailwind in `vite.config.ts`
3. Update CSS imports in `src/index.css`
4. Configure TypeScript path aliases (3 files)
5. Update Vite config with path alias resolution
6. Initialize shadcn/ui with CLI
7. Add test component (Button)
8. Verify rendering and IntelliSense

### Dependencies to Add
```json
{
  "dependencies": {
    // shadcn will add: class-variance-authority, clsx, tailwind-merge, etc.
  },
  "devDependencies": {
    "tailwindcss": "latest",
    "@tailwindcss/vite": "latest"
  }
}
```

### Configuration Files to Modify
1. `vite.config.ts` - Add Tailwind plugin + path alias
2. `tsconfig.json` - Add path aliases
3. `tsconfig.app.json` - Add path aliases
4. `src/index.css` - Replace with Tailwind import

### Configuration Files to Create
1. `components.json` - Created by shadcn CLI
2. `tailwind.config.ts` - Optional (only if customization needed)
3. `src/lib/utils.ts` - Created by shadcn CLI
4. `src/components/ui/` - Created by shadcn CLI when adding components

## Official Documentation References

- **Tailwind CSS v4 + Vite:** https://tailwindcss.com/docs/installation/using-vite
- **shadcn/ui + Vite:** https://ui.shadcn.com/docs/installation/vite
- **Vite Path Aliases:** https://vite.dev/config/shared-options.html#resolve-alias
