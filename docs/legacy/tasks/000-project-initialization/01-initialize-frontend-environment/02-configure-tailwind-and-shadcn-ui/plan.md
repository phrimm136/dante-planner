# Implementation Plan

## Task Overview

Install and configure Tailwind CSS v4 and shadcn/ui component library for the Vite + React + TypeScript frontend project. This will provide:
- Utility-first CSS framework (Tailwind CSS v4) for rapid styling
- Pre-built, accessible React components (shadcn/ui) styled with Tailwind
- TypeScript path aliases for clean imports (`@/components/ui/button`)
- Production-ready component system with full type safety

**Key Approach:**
- Use Tailwind CSS v4 simplified setup (no PostCSS config needed)
- Use `@tailwindcss/vite` plugin for seamless Vite integration
- Configure path aliases in multiple locations for full TypeScript + Vite support
- Let shadcn/ui CLI handle component installation

## Steps to Implementation

### 1. Install Tailwind CSS v4 Dependencies

Install Tailwind CSS and the official Vite plugin for the v4 simplified setup.

**Commands:**
```bash
cd frontend
yarn add -D tailwindcss @tailwindcss/vite
```

**What this installs:**
- `tailwindcss` - Core Tailwind CSS framework (v4)
- `@tailwindcss/vite` - Official Vite plugin for Tailwind v4

**Expected output:**
- Dependencies added to `package.json` devDependencies
- `yarn.lock` updated with new packages

### 2. Configure Vite with Tailwind Plugin

Update `vite.config.ts` to include the Tailwind plugin and configure path aliases for module resolution.

**File:** `frontend/vite.config.ts`

**Changes:**
```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
})
```

**What this does:**
- Adds Tailwind plugin to Vite's plugin array
- Configures `@/` alias to resolve to `./src/` for runtime module resolution

### 3. Update CSS Imports for Tailwind

Replace the existing CSS in `src/index.css` with Tailwind imports using the v4 syntax.

**File:** `frontend/src/index.css`

**Replace entire content with:**
```css
@import "tailwindcss";
```

**Note:** This is the Tailwind v4 simplified syntax. The old v3 syntax used separate `@tailwind` directives.

### 4. Configure TypeScript Path Aliases - Root Config

Add path alias configuration to the root TypeScript config for type checking.

**File:** `frontend/tsconfig.json`

**Update compilerOptions:**
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

### 5. Configure TypeScript Path Aliases - App Config

Add path alias configuration to the app-specific TypeScript config (where actual application code is compiled).

**File:** `frontend/tsconfig.app.json`

**Add to compilerOptions:**
```json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    },
    // ... existing options
  }
}
```

**Why needed:** Vite splits TypeScript config into multiple files. The app config is used for source code in `src/`.

### 6. Verify Current Configuration

Check that all configurations are correct before proceeding with shadcn/ui installation.

**Commands:**
```bash
# Verify Tailwind is installed
grep -E "tailwindcss|@tailwindcss/vite" package.json

# Verify path aliases in tsconfig files
grep -A 3 "baseUrl" tsconfig.json
grep -A 3 "baseUrl" tsconfig.app.json

# Verify vite config
cat vite.config.ts
```

### 7. Initialize shadcn/ui CLI

Run the shadcn/ui initialization command to set up the component system.

**Commands:**
```bash
yarn dlx shadcn@latest init
```

**Expected prompts and responses:**
- **"Which style would you like to use?"** → Select "New York" or "Default" (either works)
- **"Which color would you like to use as base color?"** → Select "Neutral" (recommended)
- **"Do you want to use CSS variables for colors?"** → Yes (recommended)
- **"Where is your global CSS file?"** → `src/index.css` (should auto-detect)
- **"Would you like to use TypeScript?"** → Yes
- **"Where is your tailwind.config.ts located?"** → Accept default or specify if prompted
- **"Configure the import alias for components?"** → `@/components`
- **"Configure the import alias for utils?"** → `@/lib/utils`

**What this creates:**
- `components.json` - shadcn configuration file
- `src/lib/utils.ts` - Utility functions (cn helper)
- `src/components/` - Components directory structure
- May create `tailwind.config.ts` if needed for customization

### 8. Add Button Component for Testing

Install the Button component from shadcn/ui to verify the setup works.

**Commands:**
```bash
yarn dlx shadcn@latest add button
```

**What this creates:**
- `src/components/ui/button.tsx` - Button component with variants
- Updates `src/lib/utils.ts` if needed
- Installs additional dependencies (class-variance-authority, clsx, tailwind-merge)

### 9. Test Button Component Rendering

Modify `App.tsx` to use the Button component and verify Tailwind styling works.

**File:** `frontend/src/App.tsx`

**Update to:**
```typescript
import { Button } from '@/components/ui/button'

function App() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="text-center">
        <h1 className="text-4xl font-bold text-gray-900 mb-4">
          Limbus Planner
        </h1>
        <p className="text-gray-600 mb-6">
          Tailwind CSS and shadcn/ui configured successfully!
        </p>
        <Button onClick={() => alert('Button clicked!')}>
          Click Me
        </Button>
      </div>
    </div>
  )
}

export default App
```

### 10. Start Dev Server and Verify

Start the development server and verify that:
- Tailwind styles are applied
- Button component renders correctly
- No console errors
- Hot module replacement works

**Commands:**
```bash
yarn dev
```

**Verification checklist:**
- ✅ Dev server starts without errors
- ✅ Tailwind utility classes (bg-gray-100, text-4xl, etc.) are applied
- ✅ Button component renders with shadcn/ui styling
- ✅ Button click handler works
- ✅ No TypeScript errors in console
- ✅ No import errors for `@/components/ui/button`

### 11. Test Tailwind IntelliSense (Optional)

Verify that Tailwind CSS IntelliSense works in VS Code.

**Steps:**
1. Open `App.tsx` in VS Code
2. Start typing a className: `className="bg-`
3. Verify IntelliSense shows Tailwind class suggestions

**If IntelliSense doesn't work:**
- Install "Tailwind CSS IntelliSense" VS Code extension
- Reload VS Code window
- Check that `tailwindcss` is in devDependencies

### 12. Run Production Build

Verify that the production build completes successfully with Tailwind.

**Commands:**
```bash
yarn build
```

**Expected output:**
- TypeScript compilation succeeds
- Vite build completes
- Tailwind CSS is processed and purged
- Optimized CSS bundle in `dist/assets/`

## Timeline

| Step | Task | Estimated Time |
|------|------|----------------|
| 1 | Install Tailwind dependencies | 1 minute |
| 2 | Configure Vite | 1 minute |
| 3 | Update CSS imports | 1 minute |
| 4-5 | Configure TypeScript path aliases | 2 minutes |
| 6 | Verify configuration | 1 minute |
| 7 | Initialize shadcn/ui | 2 minutes |
| 8 | Add Button component | 1 minute |
| 9 | Update App.tsx for testing | 2 minutes |
| 10 | Start dev server and verify | 2 minutes |
| 11 | Test IntelliSense (optional) | 1 minute |
| 12 | Run production build | 1 minute |
| **Total** | | **~15 minutes** |

## Success Criteria

- ✅ Tailwind CSS v4 installed with `@tailwindcss/vite` plugin
- ✅ `vite.config.ts` includes Tailwind plugin and path aliases
- ✅ `src/index.css` uses `@import "tailwindcss";` syntax
- ✅ TypeScript path aliases configured in `tsconfig.json` and `tsconfig.app.json`
- ✅ shadcn/ui initialized with `components.json` created
- ✅ Button component installed in `src/components/ui/button.tsx`
- ✅ `src/lib/utils.ts` created with cn helper function
- ✅ App.tsx renders Button with Tailwind styling
- ✅ No TypeScript errors for `@/` imports
- ✅ Dev server runs without errors
- ✅ Production build completes successfully
- ✅ Tailwind IntelliSense works (optional but recommended)

## Potential Issues & Mitigations

### Issue: Yarn DLX Not Working

**Symptoms:** `yarn dlx shadcn` fails or command not recognized

**Mitigation:**
```bash
# Use npx instead
npx shadcn@latest init
npx shadcn@latest add button
```

### Issue: Path Alias Import Errors

**Symptoms:**
```
Cannot find module '@/components/ui/button' or its corresponding type declarations
```

**Mitigation:**
- Verify `baseUrl` and `paths` in both `tsconfig.json` AND `tsconfig.app.json`
- Verify `resolve.alias` in `vite.config.ts`
- Restart TypeScript server in VS Code: Cmd/Ctrl + Shift + P → "TypeScript: Restart TS Server"

### Issue: Tailwind Styles Not Applied

**Symptoms:** Components render but have no styling

**Mitigation:**
- Verify `@import "tailwindcss";` is in `src/index.css`
- Verify `src/index.css` is imported in `src/main.tsx`
- Check that `tailwindcss()` plugin is in `vite.config.ts`
- Clear Vite cache: `rm -rf node_modules/.vite` and restart dev server

### Issue: shadcn Init Fails

**Symptoms:** CLI hangs or errors during initialization

**Mitigation:**
- Ensure Tailwind is installed first
- Ensure path aliases are configured before running init
- Try using npx instead of yarn dlx
- Check internet connection (CLI fetches component templates)

### Issue: React 19 Compatibility Warnings

**Symptoms:** Console warnings about React version

**Mitigation:**
- shadcn/ui and Radix UI support React 19
- Warnings are usually safe to ignore
- Update shadcn components if they exist: `npx shadcn@latest diff`

### Issue: PostCSS Config Confusion

**Symptoms:** Old guides mention `postcss.config.js`

**Mitigation:**
- **Do NOT create** `postcss.config.js` for Tailwind v4
- The `@tailwindcss/vite` plugin handles PostCSS automatically
- If you already created it, delete it

## File Changes Summary

### Files to Modify
1. `frontend/vite.config.ts` - Add Tailwind plugin + path alias
2. `frontend/src/index.css` - Replace with Tailwind import
3. `frontend/tsconfig.json` - Add path aliases
4. `frontend/tsconfig.app.json` - Add path aliases
5. `frontend/src/App.tsx` - Test Button component
6. `frontend/package.json` - Updated by yarn (dependencies)

### Files to Create (by CLI)
1. `frontend/components.json` - shadcn config
2. `frontend/src/lib/utils.ts` - Utility functions
3. `frontend/src/components/ui/button.tsx` - Button component
4. `frontend/tailwind.config.ts` - Optional (may be created by CLI)

### Files NOT to Create
- ❌ `postcss.config.js` - Not needed for Tailwind v4
- ❌ `frontend/styles/globals.css` - Use `src/index.css` instead

## Next Steps (After Completion)

1. Add more shadcn/ui components as needed (Card, Dialog, Form, etc.)
2. Customize Tailwind configuration if needed (colors, fonts, etc.)
3. Set up global CSS variables for theming
4. Create custom components using shadcn/ui primitives
5. Configure Tailwind IntelliSense workspace settings
