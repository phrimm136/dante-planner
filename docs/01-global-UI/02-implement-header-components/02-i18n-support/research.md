# Research: i18n Support Implementation

## Overview of Codebase

**Current i18n State**:
- No i18n libraries installed (react-i18next, i18next not in dependencies)
- No translation infrastructure exists
- All text is hardcoded in English
- HTML lang attribute hardcoded to "en" in index.html
- No localStorage or language persistence mechanism

**Required Dependencies**:
- i18next (core i18n framework)
- react-i18next (React bindings with hooks)
- i18next-browser-languagedetector (auto-detect and persist language preference)

**Hardcoded Text Locations**:
- Header component: navigation labels, aria-labels for icon buttons
- InfoPage, PlannerPage, CommunityPage: headings, descriptions, "Back to Home" button
- Exception: "Dante's Planner" title must NOT be translated per requirements

**Dropdown Pattern**:
- Need to install shadcn DropdownMenu component (not yet in project)
- Based on Radix UI with built-in accessibility
- Use DropdownMenuRadioGroup for single language selection with visual indicator

## Codebase Structure

**Translation File Organization**:
- Directory structure: static/i18n/EN/, static/i18n/JP/, static/i18n/KR/, static/i18n/CN/
- Single common.json per language for MVP (can split later by purpose)
- Nested structure recommended: header.nav.info instead of flat headerNavInfo
- User will populate translation content (leave empty skeleton for now)

**Configuration Files**:
- Create i18n.ts for i18next initialization and configuration
- Import in main.tsx before app rendering to initialize translations
- Set fallbackLng to "EN" to handle missing translations

**Component Integration**:
- Use useTranslation() hook in components to access translation function
- Update HTML lang attribute dynamically via useEffect when language changes
- Language selector replaces placeholder Languages button functionality

**State Management**:
- i18next-browser-languagedetector handles localStorage automatically
- Stores preference in localStorage key "i18nextLng"
- Auto-detects from: localStorage > browser language > fallback

## Gotchas and Pitfalls

**Vite Static File Serving**:
- Vite serves static/ folder content at root /static/ path
- Translation files accessible at /static/i18n/EN/common.json
- Configure i18next loadPath to match this structure

**React 19 Compatibility**:
- react-i18next fully supports React 19 concurrent features
- Safe to use Suspense with i18next if needed for loading states

**Translation Key Naming**:
- Avoid English words as keys (causes duplication with English translations)
- Use semantic keys: "nav.info" not "inGameInfo"
- Prevents confusion between key names and actual English text

**Missing Translations**:
- Configure fallbackLng to prevent empty strings when translations missing
- Missing keys will show fallback language (EN) instead of breaking UI

**DropdownMenu Installation**:
- Requires @radix-ui/react-dropdown-menu peer dependency
- Auto-installed by shadcn CLI when adding DropdownMenu component
