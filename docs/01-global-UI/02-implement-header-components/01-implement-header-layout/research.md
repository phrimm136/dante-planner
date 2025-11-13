# Research: Implement Header Layout

## Overview of Codebase

### Current State

The Header component at `frontend/src/components/Header.tsx` is currently a minimal placeholder with only a title:
- Displays "Dante's Planner" as an h1 element
- Basic padding (`px-6 py-4`)
- No navigation, links, or interactive elements

**What Needs to Change**: Complete enhancement from simple title to full-featured header with three sections: title/logo (left), navigation (center), and settings (right).

### Existing Patterns and Standards

#### 1. Component Architecture

**Button Component** (`frontend/src/components/ui/button.tsx`):
- Well-implemented shadcn button with multiple variants
- Available variants: `default`, `destructive`, `outline`, `secondary`, `ghost`, `link`
- Size options: `default`, `sm`, `lg`, `icon`, `icon-sm`, `icon-lg`
- Supports `asChild` prop for composition with TanStack Router Link
- Uses class-variance-authority (CVA) for variant management
- Includes accessibility features (focus-visible states, aria support)

**Utility Functions**:
- `cn()` utility at `frontend/src/lib/utils.ts` for className merging
- Uses `clsx` + `tailwind-merge` for optimal className handling
- Standard pattern in shadcn components

#### 2. Styling System

**Theme Tokens** (from `frontend/src/styles/globals.css`):
- Uses OKLCH color space with CSS custom properties
- Semantic color tokens available:
  - Backgrounds: `bg-background`, `bg-card`, `bg-popover`
  - Foregrounds: `text-foreground`, `text-muted-foreground`
  - Interactive: `bg-primary`, `bg-secondary`, `bg-accent`
  - Borders: `border-border`
  - Focus: `ring-ring`
- Complete light/dark mode support via `.dark` class
- All semantic tokens have automatic theme switching

**Current Spacing Patterns**:
- Horizontal padding: `px-6` (used in Header and Footer)
- Vertical padding: `py-4` (used in Header and Footer)
- Button gaps: `gap-2` (compact)
- Navigation spacing: `gap-6` recommended (generous)
- Section spacing: `gap-4` or `gap-6` (medium)

**Typography**:
- Current header title: `text-2xl font-bold`
- Muted text: `text-muted-foreground`
- Standard body text uses system defaults

#### 3. Navigation and Routing

**TanStack Router Integration**:
- Import pattern: `import { Link } from '@tanstack/react-router'`
- Used in HomePage and AboutPage for navigation
- Composition pattern with Button component:
  ```tsx
  <Button asChild variant="outline">
    <Link to="/">Back to Home</Link>
  </Button>
  ```

**Available Routes** (from `frontend/src/lib/router.tsx`):
- `/` - HomePage
- `/about` - AboutPage (test route)

**Required Navigation Links** (from task description):
- In-Game Info (route doesn't exist yet - use placeholder)
- Planner (route doesn't exist yet - use placeholder)
- Community (route doesn't exist yet - use placeholder)

#### 4. Icon System

**lucide-react v0.553.0**:
- Installed and available but not yet used in codebase
- Recommended icons for header:
  - `Languages` - Language selector
  - `Sun` / `Moon` - Theme toggle
  - `Settings` - Settings menu
  - `LogIn` / `LogOut` / `User` - Authentication
  - `Menu` - Mobile hamburger menu (future)

**Usage Pattern**:
```tsx
import { Settings, Sun, Moon } from 'lucide-react'

<Button variant="ghost" size="icon" aria-label="Settings">
  <Settings />
</Button>
```

#### 5. Layout Patterns

**Flexbox Patterns Observed**:
- Horizontal layout: `flex items-center justify-between`
- Column layout: `flex flex-col`
- Centering: `flex items-center justify-center`
- Spacing: `gap-2`, `gap-4`, `gap-6`, or `space-x-*`

**GlobalLayout Integration** (from `frontend/src/components/GlobalLayout.tsx`):
- Header is wrapped in: `<div className="bg-card border-b border-border">`
- Header component doesn't need to add its own background/border
- Wrapper provides `bg-card` background and border-bottom
- Header only needs internal layout structure

### Frameworks and Standards

**TypeScript**:
- Strict TypeScript throughout
- Proper interface definitions for props
- Path aliases configured (`@/components`, `@/lib`, etc.)

**Naming Conventions** (from tech-brief.md):
- PascalCase for all files and components
- Pattern: `{Entity}{Purpose}.tsx`
- Current `Header.tsx` follows convention

**Component Patterns**:
- Functional components with named exports for UI components
- Props interfaces defined above component
- Use of `cn()` for className management
- Semantic HTML elements (`<header>`, `<nav>`, etc.)

### How This Relates to the Task

**Task Requirements**:
1. **Left Section**: Title linking to root - use TanStack Router Link
2. **Center Section**: Navigation links - use Button + Link composition with ghost variant
3. **Right Section**: Settings buttons - use icon buttons with ghost variant

**Available Building Blocks**:
- ✅ Button component with all needed variants
- ✅ TanStack Router Link for navigation
- ✅ lucide-react icons for settings buttons
- ✅ Theme tokens for styling
- ✅ Flexbox utilities for layout
- ✅ `cn()` utility for className management

**Missing Elements** (will need placeholders):
- ❌ Navigation routes (In-Game Info, Planner, Community pages don't exist)
- ❌ Theme switching logic (CSS variables exist, but no toggle implementation)
- ❌ i18n system (noted as high priority in previous findings)
- ❌ Authentication system (backend will use OAuth 2.0 + JWT per PRD)

## Codebase Structure

### Relevant File Locations

```
frontend/src/
├── components/
│   ├── Header.tsx                    # TARGET FILE - needs enhancement
│   ├── Footer.tsx                    # Similar simple component
│   ├── GlobalLayout.tsx              # Wraps Header with bg-card
│   └── ui/
│       └── button.tsx                # Shadcn button component (ready to use)
├── lib/
│   ├── router.tsx                    # Route definitions
│   └── utils.ts                      # cn() utility
├── routes/
│   ├── __root.tsx                    # Root route (minimal)
│   ├── HomePage.tsx                  # Has Link examples
│   └── AboutPage.tsx                 # Has Link examples
├── styles/
│   └── globals.css                   # Theme tokens and CSS variables
└── main.tsx                          # Entry point
```

### Component Organization

**Current Structure**:
- Custom components in `src/components/`
- Shadcn UI components in `src/components/ui/`
- Utilities in `src/lib/`
- Routes in `src/routes/`

**Header Component Location**:
- Path: `frontend/src/components/Header.tsx`
- Imported by: `GlobalLayout.tsx`
- Used in: Root layout for all routes

### Integration Points

**GlobalLayout.tsx**:
```tsx
<div className="bg-card border-b border-border">
  <Header />  // Our target component
</div>
```

**Key Point**: Header component receives styling context from wrapper:
- Background color: `bg-card` (from wrapper)
- Border: `border-b border-border` (from wrapper)
- Header itself only needs internal layout

**Router.tsx**:
- Defines available routes
- Header will need to link to these routes
- Currently only `/` and `/about` exist

## Gotchas and Pitfalls

### 1. Don't Add Background/Border to Header Component

**Issue**: Header is wrapped in a div with `bg-card border-b border-border` in GlobalLayout.

**What NOT to Do**:
```tsx
// ❌ DON'T - This duplicates styling from wrapper
export function Header() {
  return (
    <header className="bg-card border-b border-border px-6 py-4">
      {/* ... */}
    </header>
  )
}
```

**What TO Do**:
```tsx
// ✅ DO - Let wrapper handle background and border
export function Header() {
  return (
    <header className="px-6 py-4">
      {/* Internal layout only */}
    </header>
  )
}
```

**Why**: Separation of concerns - GlobalLayout handles background/borders, Header handles internal layout.

### 2. Navigation Routes Don't Exist Yet

**Issue**: Task requires links to "In-Game Info", "Planner", and "Community" pages, but these routes aren't defined in router.tsx.

**Solution**: Use placeholder paths that follow expected routing structure:
- `/info` or `/game-info` for In-Game Info
- `/planner` for Planner
- `/community` for Community

**Note**: These routes will return 404 until pages are created. This is acceptable for layout implementation.

**Future**: When routes are created, update the paths in Header component.

### 3. Theme Toggle Has No Implementation

**Issue**: Task requires light/dark mode toggle button, but no theme switching logic exists.

**Current State**:
- CSS variables for light/dark mode exist in globals.css
- `.dark` class triggers dark mode
- No JavaScript/React logic to toggle the class

**Solution for MVP**:
- Add button with Sun/Moon icon
- Make it a placeholder (no onClick handler yet)
- Add TODO comment for future implementation
- Consider adding disabled state or tooltip: "Coming Soon"

**Future Implementation** will need:
```tsx
// Future: Theme context and toggle logic
const toggleTheme = () => {
  document.documentElement.classList.toggle('dark')
}
```

### 4. No i18n System

**Issue**: Language selector button required, but no internationalization system exists.

**Context**: PRD requires multi-language support. Previous findings.md noted this as high priority.

**Solution for MVP**:
- Add language button with Languages icon
- Make it a placeholder (no functionality)
- Add TODO comment for future i18n implementation

**Future**: Will need react-i18next or similar library.

### 5. No Authentication System

**Issue**: Sign In/Out button required, but no auth system exists.

**Context**: PRD mentions OAuth 2.0 + JWT for backend (not yet implemented).

**Solution for MVP**:
- Add sign-in button with LogIn or User icon
- Make it a placeholder (no functionality)
- Add TODO comment for future auth implementation

**Future**: Will need auth context/hooks and backend integration.

### 6. Responsive Design Not Implemented

**Current State**:
- Fixed padding across all screen sizes
- No mobile-specific layouts
- Previous findings.md noted responsive design as needed

**For This Task**:
- Desktop-first approach acceptable
- Full horizontal layout on desktop
- Mobile optimization can be future enhancement

**Future Considerations**:
- Mobile (<640px): Hamburger menu, hide center nav
- Tablet (640px-1024px): Reduce spacing, keep all elements visible
- Desktop (>1024px): Full layout with generous spacing

**Recommendation**: Implement desktop layout first, add responsive utilities in future task.

### 7. Missing `cn()` Utility Usage in Current Header

**Issue**: Current Header.tsx doesn't import or use `cn()` utility.

**Pattern to Follow**: All shadcn components use `cn()` for className management.

**Should Import**:
```tsx
import { cn } from '@/lib/utils'
```

**Use Case**: For conditional classes or className prop support (if added).

### 8. Link Styling Considerations

**Issue**: Default Link component has blue color and underline (browser defaults).

**Solution**: Use Button component with `asChild` prop:
```tsx
// ✅ Styled as button (recommended)
<Button asChild variant="ghost">
  <Link to="/planner">Planner</Link>
</Button>

// ❌ Unstyled link (browser defaults)
<Link to="/planner">Planner</Link>
```

**For Title Link**: Remove link appearance except on hover:
```tsx
<Link
  to="/"
  className="text-foreground no-underline hover:text-primary transition-colors"
>
  Dante's Planner
</Link>
```

### 9. Active Route Indication

**Consideration**: Users need visual feedback for current page.

**TanStack Router Feature**: Link component has `activeProps` and `activeOptions`:
```tsx
<Link
  to="/planner"
  activeProps={{ className: "text-primary" }}
  activeOptions={{ exact: true }}
>
  Planner
</Link>
```

**Or with Button**:
```tsx
// Check active state manually
const isActive = // ... router state check
<Button asChild variant={isActive ? "default" : "ghost"}>
  <Link to="/planner">Planner</Link>
</Button>
```

**For MVP**: Can be omitted, add in future enhancement.

### 10. Icon Button Accessibility

**Issue**: Icon-only buttons need accessible labels.

**Solution**: Use `aria-label` attribute:
```tsx
<Button
  variant="ghost"
  size="icon"
  aria-label="Toggle theme"
>
  <Sun />
</Button>
```

**Or**: Add tooltip component (shadcn has Tooltip component available via CLI).

**For MVP**: `aria-label` is sufficient.

### 11. Proportions and Spacing

**Research Requirement**: "Find the proper proportion"

**Recommended Layout Structure**:
```tsx
<header className="px-6 py-4">
  <div className="flex items-center justify-between gap-4">
    {/* Left: Fixed width, shrinks if needed */}
    <div className="flex-shrink-0">
      {/* Title/Logo */}
    </div>

    {/* Center: Flexible, grows to accommodate */}
    <nav className="flex items-center gap-6">
      {/* Navigation links */}
    </nav>

    {/* Right: Fixed width, auto based on buttons */}
    <div className="flex-shrink-0 flex items-center gap-2">
      {/* Settings buttons */}
    </div>
  </div>
</header>
```

**Gap Recommendations**:
- Between sections: `gap-4` (16px)
- Between nav links: `gap-6` (24px) - generous spacing for readability
- Between icon buttons: `gap-2` (8px) - compact grouping
- Within sections: `items-center` for vertical alignment

**Proportions**:
- Left: ~150-250px (natural width of title)
- Center: Flexible, centered navigation
- Right: ~150-200px (4 icon buttons × 36px + gaps)

**Alternative**: Use `justify-between` instead of `flex-shrink-0`:
- Simpler implementation
- Left section stays left
- Right section stays right
- Center nav fills middle space

### 12. Pre-existing Issues

**From previous findings.md**:
- TypeScript errors in `src/test-utils/` (unrelated, blocks builds)
- No responsive padding utilities
- No automated tests for components

**Impact on This Task**: None directly, but be aware that production builds may fail due to test-utils issues.

### 13. Performance Considerations

**From previous review.md**: Header component re-renders on every route change.

**For This Task**: Acceptable behavior for MVP.

**Future Optimization**: Consider React.memo if performance profiling shows issues:
```tsx
export const Header = memo(function Header() {
  // ...
})
```

## Summary

### What We Have
✅ Excellent Button component with all needed variants
✅ TanStack Router well-integrated with Link component
✅ lucide-react icons available
✅ Complete theme token system with dark mode
✅ Clean utility functions (cn)
✅ Clear flexbox layout patterns
✅ Semantic HTML standards

### What We Need to Add
📝 Three-section horizontal layout (left/center/right)
📝 Title/logo link to root page
📝 Navigation links (with placeholder routes)
📝 Settings icon buttons (with placeholder functionality)
📝 Proper spacing and proportions
📝 Accessibility labels for icon buttons

### What's Missing (Acceptable Placeholders)
⚠️ Theme toggle logic (button only)
⚠️ Language selector logic (button only)
⚠️ Authentication system (button only)
⚠️ Actual navigation pages (use placeholder routes)
⚠️ i18n system (hardcoded text acceptable)
⚠️ Responsive mobile layout (desktop-first acceptable)

### Implementation Confidence
**High** - All necessary building blocks are available, patterns are clear, and implementation is straightforward with existing tools.

**Estimated Complexity**: Medium
- Layout structure: Simple (clear flexbox pattern)
- Navigation links: Simple (Button + Link pattern established)
- Icon buttons: Simple (Button component ready, icons available)
- Placeholder buttons: Simple (just visual, no logic)
- Responsive design: Medium (if implemented, but optional for MVP)

**Overall**: Straightforward implementation with well-prepared codebase.
