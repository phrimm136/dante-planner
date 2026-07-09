# Implementation Plan: i18n Support

## Task Overview

Implement internationalization (i18n) to support multiple languages (EN, JP, KR, CN) with a language selector dropdown. Users can click the Languages icon button to open a dropdown menu and select their preferred language. All text except "Dante's Planner" title will be translatable. Translation content will be provided by the user later.

## Steps to Implementation

1. **Install Dependencies**: Add i18next, react-i18next, and i18next-browser-languagedetector packages via yarn.

2. **Install DropdownMenu Component**: Use shadcn CLI to add DropdownMenu component for language selector UI.

3. **Create Translation File Structure**: Create static/i18n/ directory with EN/, JP/, KR/, CN/ subdirectories. Add empty common.json skeleton in each.

4. **Configure i18next**: Create frontend/src/lib/i18n.ts with i18next configuration, fallback language, and static file loading path.

5. **Initialize i18n**: Import and initialize i18n configuration in main.tsx before app rendering.

6. **Build Language Selector Dropdown**: Replace placeholder Languages button in Header with functional DropdownMenu containing four language options.

7. **Replace Hardcoded Text**: Update Header, InfoPage, PlannerPage, CommunityPage to use useTranslation hook instead of hardcoded strings.

8. **Update HTML Lang Attribute**: Add useEffect in main app to dynamically update document.documentElement.lang when language changes.

9. **Test Language Switching**: Verify dropdown opens, language selection works, localStorage persists preference, and UI updates correctly.

10. **Document Translation Keys**: Create key reference document so user knows what to translate in JSON files.

## Timeline

| Step | Time | Cumulative |
|------|------|------------|
| 1    | 5min | 5min       |
| 2    | 5min | 10min      |
| 3    | 5min | 15min      |
| 4    | 10min| 25min      |
| 5    | 5min | 30min      |
| 6    | 15min| 45min      |
| 7    | 15min| 60min      |
| 8    | 5min | 65min      |
| 9    | 10min| 75min      |
| 10   | 5min | 80min      |
| **Total** | **80min** |     |

## Success Criteria

- Dependencies installed and configured correctly
- Translation directory structure created (static/i18n/EN|JP|KR|CN/)
- Language dropdown appears when Languages button clicked
- Selecting language changes UI text immediately
- Language preference persists after page reload (localStorage)
- HTML lang attribute updates dynamically
- All hardcoded text replaced with translation keys except "Dante's Planner"
- Empty JSON skeletons ready for user to populate translations
