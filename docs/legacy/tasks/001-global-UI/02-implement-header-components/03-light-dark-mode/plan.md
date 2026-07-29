# Implementation Plan: Light/Dark Mode

## Task Overview

Implement theme switching between light and dark modes with browser preference detection. User can toggle theme via button in header that switches between Sun and Moon icons. Theme preference persists to localStorage and respects system preferences on first visit, defaulting to dark mode if detection fails.

## Steps to Implementation

1. **Create Theme Context and Provider**: Build React context to manage theme state (light/dark) with initial value from localStorage or browser preference detection. Export context and provider component.

2. **Build useTheme Custom Hook**: Create hook to access theme context, providing current theme value and toggle function. Follow existing hook patterns in hooks directory.

3. **Add Browser Preference Detection**: Implement function using window.matchMedia to check prefers-color-scheme. Return dark, light, or null if unsupported. Use as fallback when no localStorage value exists.

4. **Create ThemeSync Component**: Build component following LanguageSync pattern that applies or removes dark class on html element when theme changes. Use useEffect to watch theme state.

5. **Integrate Theme Provider**: Wrap application with ThemeProvider in main.tsx or router configuration. Ensure provider wraps all components that need theme access.

6. **Add ThemeSync to GlobalLayout**: Import and render ThemeSync component in GlobalLayout alongside existing LanguageSync component.

7. **Update Header Theme Button**: Replace placeholder button with functional implementation using useTheme hook. Conditionally render Sun icon for light mode, Moon icon for dark mode. Add click handler to toggle theme.

8. **Add localStorage Persistence**: Implement storage event listener for cross-tab synchronization. Save theme to localStorage on every change with consistent key name.

9. **Test Theme Switching**: Verify button toggles theme, icon changes correctly, preference persists across page refreshes, and browser preference detection works on first visit.

10. **Handle Flash Prevention**: Add inline script to index.html that applies theme class before React hydration to prevent flash of wrong theme on page load.

## Timeline

| Step | Time | Cumulative |
|------|------|------------|
| 1    | 10min | 10min     |
| 2    | 5min  | 15min     |
| 3    | 10min | 25min     |
| 4    | 5min  | 30min     |
| 5    | 5min  | 35min     |
| 6    | 3min  | 38min     |
| 7    | 10min | 48min     |
| 8    | 7min  | 55min     |
| 9    | 10min | 65min     |
| 10   | 5min  | 70min     |
| Total| 70min |           |

## Success Criteria

- Clicking theme button toggles between light and dark modes with visual changes throughout application
- Button icon switches between Sun (light mode) and Moon (dark mode) appropriately
- Theme preference persists to localStorage and survives page refreshes
- Browser preference detection works on first visit using prefers-color-scheme media query
- Dark theme used as default when browser preference cannot be detected
- Theme changes synchronize across multiple browser tabs immediately
- No flash of unstyled content or wrong theme during initial page load
- All existing UI components render correctly in both light and dark modes
