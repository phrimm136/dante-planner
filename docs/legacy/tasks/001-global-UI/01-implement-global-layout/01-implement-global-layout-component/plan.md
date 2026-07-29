# Implementation Plan: Global Layout Component

## Task Overview

Implement a GlobalLayout component that provides a consistent header-body-footer structure for all routes in the Dante's Planner application. The layout will use Tailwind CSS v4 and shadcn styling patterns, with semantic color tokens for proper light/dark mode support.

**Key Requirements**:
- Header always at the top
- Footer always at the bottom
- Body fills the space between header and footer
- Header and footer share the same background color (`bg-card`)
- Body uses global background color (`bg-background`)
- Dark mode support via CSS variables

## Steps to Implementation

### Step 1: Fix CSS Import Issue
**What**: Correct the broken CSS import in the main entry point

**Actions**:
- Update `frontend/src/main.tsx` line 8
- Change `import './index.css'` to `import './styles/globals.css'`

**Why**: The current import references a non-existent file, which may prevent global styles and theme from loading properly.

**Validation**: Verify that globals.css is properly loaded in the browser DevTools

---

### Step 2: Create GlobalLayout Component
**What**: Build the main layout component with header, body, and footer sections

**Location**: `frontend/src/components/GlobalLayout.tsx`

**Implementation Details**:
```tsx
// Component structure:
- Container: min-h-screen flex flex-col
  - Header: bg-card border-b border-border
  - Main (Body): flex-1 bg-background
    - {children} // Outlet will be rendered here
  - Footer: bg-card border-t border-border
```

**Styling Approach**:
- Use Flexbox with `flex-col` for vertical stacking
- Apply `min-h-screen` to ensure footer stays at bottom
- Use `flex-1` on main content to fill available space
- Use semantic color tokens: `bg-card`, `bg-background`, `border-border`
- Apply proper spacing and padding

**TypeScript Typing**:
```tsx
interface GlobalLayoutProps {
  children: React.ReactNode
}

export function GlobalLayout({ children }: GlobalLayoutProps) {
  // ...
}
```

**Component Pattern**:
- Follow shadcn component patterns (see `button.tsx` as reference)
- Use `cn()` utility from `@/lib/utils` for className merging
- Export as named export: `export function GlobalLayout`

---

### Step 3: Create Header Component (Placeholder)
**What**: Create a simple header component for initial implementation

**Location**: `frontend/src/components/Header.tsx`

**Implementation**:
- Simple placeholder with app title "Dante's Planner"
- Padding and basic styling
- Export as named export

**Future Considerations**:
- Navigation menu (to be added later)
- User authentication UI (to be added later)
- Language selector (multi-language requirement)

---

### Step 4: Create Footer Component (Placeholder)
**What**: Create a simple footer component for initial implementation

**Location**: `frontend/src/components/Footer.tsx`

**Implementation**:
- Simple placeholder with copyright or basic info
- Padding and basic styling
- Export as named export

**Future Considerations**:
- Links to documentation, GitHub, etc.
- Version information
- Social links

---

### Step 5: Integrate GlobalLayout into Router
**What**: Update the router configuration to use the new GlobalLayout

**Location**: `frontend/src/lib/router.tsx`

**Changes**:
1. Import GlobalLayout: `import { GlobalLayout } from '@/components/GlobalLayout'`
2. Update the rootRoute component (lines 8-15):

```tsx
// Before:
const rootRoute = createRootRoute({
  component: () => (
    <div className="min-h-screen bg-background text-foreground">
      <Outlet />
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </div>
  ),
})

// After:
const rootRoute = createRootRoute({
  component: () => (
    <>
      <GlobalLayout>
        <Outlet />
      </GlobalLayout>
      {import.meta.env.DEV && <TanStackRouterDevtools position="bottom-right" />}
    </>
  ),
})
```

**Important**: Keep the TanStackRouterDevtools outside of GlobalLayout to avoid interference with the layout structure.

---

### Step 6: Manual Testing
**What**: Verify the layout works correctly across different scenarios

**Test Cases**:
1. **Header Position**: Verify header is at the top of the page
2. **Footer Position**: Verify footer is at the bottom of the page
3. **Body Content**: Verify body area renders between header and footer
4. **Color Differentiation**:
   - Verify header/footer share the same background color
   - Verify header/footer background differs from body background
5. **Dark Mode**: Toggle dark mode and verify colors work correctly
6. **Content Overflow**: Test with minimal and extensive content
   - Minimal content: Footer should stay at bottom
   - Long content: Footer should appear after scrolling
7. **Navigation**: Navigate between routes (/ and /about) to ensure layout persists

**Testing Method**:
- Start dev server: `yarn dev`
- Open browser to localhost
- Visually inspect layout
- Use DevTools to verify CSS classes and computed styles
- Test dark mode toggle (if available, or manually add `.dark` class)

---

### Step 7: Automated Testing (Optional)
**What**: Write basic tests for the GlobalLayout component

**Location**: `frontend/src/components/GlobalLayout.test.tsx`

**Test Coverage**:
- Component renders without crashing
- Children are rendered correctly
- Header, main, and footer sections are present
- Proper CSS classes are applied

**Testing Utilities**:
- Use existing test utilities from `src/test-utils/`
- Follow pattern from `HomePage.test.tsx`

---

## Timeline

| Step | Estimated Time | Cumulative |
|------|---------------|------------|
| Step 1: Fix CSS Import | 2 minutes | 2 min |
| Step 2: Create GlobalLayout | 10 minutes | 12 min |
| Step 3: Create Header | 5 minutes | 17 min |
| Step 4: Create Footer | 5 minutes | 22 min |
| Step 5: Integrate into Router | 5 minutes | 27 min |
| Step 6: Manual Testing | 10 minutes | 37 min |
| Step 7: Automated Testing (Optional) | 15 minutes | 52 min |

**Total Estimated Time**: 37-52 minutes

---

## Success Criteria

The implementation will be considered successful when:

1. ✅ CSS import issue is resolved
2. ✅ GlobalLayout component is created and follows project patterns
3. ✅ Header and Footer components are created (placeholder versions)
4. ✅ Layout is integrated into the router
5. ✅ Manual testing confirms:
   - Header is always at the top
   - Footer is always at the bottom
   - Body fills the space between
   - Color differentiation is correct
   - Dark mode works properly
   - Layout persists across route navigation
6. ✅ No TypeScript errors
7. ✅ No console errors or warnings

---

## Implementation Notes

### Color Tokens Reference
From `globals.css`:
- `bg-background`: Main background (body)
- `bg-card`: Secondary background (header/footer)
- `text-foreground`: Main text color
- `border-border`: Border color

### Layout Structure
```
┌─────────────────────────────┐
│ Header (bg-card)            │
├─────────────────────────────┤
│                             │
│ Body (bg-background)        │
│ [Outlet renders here]       │
│                             │
│ (flex-1, fills space)       │
│                             │
├─────────────────────────────┤
│ Footer (bg-card)            │
└─────────────────────────────┘
```

### Accessibility Considerations
- Use semantic HTML: `<header>`, `<main>`, `<footer>`
- Ensure proper heading hierarchy (will be important for future navigation)
- Maintain focus management (TanStack Router handles this)

### Future Enhancements (Out of Scope)
- Navigation menu in header
- User authentication UI
- Language selector
- Responsive mobile menu
- Breadcrumbs
- Loading states
- Error boundaries
