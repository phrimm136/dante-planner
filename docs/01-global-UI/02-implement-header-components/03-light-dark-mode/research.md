# Research: Light/Dark Mode Implementation

## Overview of Codebase

**Existing Theme Infrastructure:**
- CSS variables using OKLch color model already defined in globals.css for both light and dark modes
- Tailwind v4 with Vite plugin configured for class-based dark mode using dark: prefix
- All UI components already use dark: variants throughout (buttons, dropdowns, cards, etc.)
- Custom dark variant defined in globals.css using selector pattern
- No React context or hooks for theme management exist yet
- Theme toggle button exists in Header with Sun icon but marked with TODO comment

**Established Patterns:**
- i18n uses localStorage with auto-detection pattern (localStorage then navigator API fallback)
- LanguageSync component pattern updates HTML attributes via useEffect when state changes
- GlobalLayout wraps all pages and includes sync components (LanguageSync)
- Custom hooks located in frontend/src/hooks directory following useExampleQuery pattern
- No Context providers for global UI state yet, only QueryClient and Router providers

**Component Structure:**
- Header component has three sections: left title, center nav, right settings buttons
- Language selector uses DropdownMenu with DropdownMenuRadioGroup for single selection
- All interactive components use ghost variant buttons with icon-only size
- Translation keys used for aria-labels following established i18n pattern

## Codebase Structure

**Theme-Related Files:**
- globals.css contains all CSS variable definitions in root and dark class selectors
- Header.tsx at lines 88-95 has placeholder theme button awaiting implementation
- GlobalLayout.tsx ideal location for ThemeSync component integration similar to LanguageSync
- No tailwind.config file exists, configuration inferred from Vite plugin and globals.css

**Color System:**
- Light mode variables in root selector with OKLch values like oklch(1 0 0) for white backgrounds
- Dark mode variables in dark class selector with darker OKLch values like oklch(0.145 0 0)
- Variables cover background, foreground, card, popover, primary, secondary, muted, accent, destructive, border, input, ring
- Chart colors and sidebar variants also defined for both modes

**File Organization:**
- Components in frontend/src/components with Header, Footer, GlobalLayout, LanguageSync
- Lib files in frontend/src/lib including i18n.ts configuration
- Hooks directory ready for useTheme custom hook
- Styles in frontend/src/styles with globals.css as single source

## Gotchas and Pitfalls

**Dark Mode Application:**
- Must apply dark class to html element not body to work with Tailwind dark: prefix
- Tailwind v4 uses custom variant syntax in globals.css not traditional config file
- Class-based approach requires manual class toggling, not automatic media query detection

**Browser Preference Detection:**
- Need window.matchMedia prefers-color-scheme media query for browser preference
- matchMedia already mocked in vitest.setup.ts but returns matches: false by default
- Must check both dark and light preferences, fallback to dark per requirements

**Storage Synchronization:**
- localStorage must sync across tabs using storage event listener
- Theme change in one tab should reflect in other open tabs immediately
- Initial load must check localStorage before applying browser preference

**Icon Switching:**
- Current button shows Sun icon from lucide-react, need Moon icon import too
- Must conditionally render based on current theme, not toggle behavior
- Sun icon shown in light mode, Moon icon shown in dark mode convention

**Component Integration:**
- LanguageSync pattern returns null but must be included in component tree
- ThemeSync needs similar useEffect pattern to apply class changes
- GlobalLayout already imports LanguageSync, similar pattern for ThemeSync

**TypeScript Considerations:**
- Theme values should be typed as literal union type not plain string
- Context value type must include both theme state and setter function
- localStorage values need type guards since getItem returns string or null

**Testing Challenges:**
- vitest.setup.ts mocks matchMedia but needs update to test preference detection
- Testing theme persistence requires mocking localStorage
- Component tests need theme context provider wrapper

**Performance Concerns:**
- Avoid flash of unstyled content by setting theme class before React hydration
- Consider inline script in index.html to apply theme immediately on load
- useEffect runs after render causing potential flash, need SSR-friendly approach