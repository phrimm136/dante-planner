# i18n Implementation Documentation

## Overview
Implemented internationalization support with 4 languages (EN, JP, KR, CN) using i18next and react-i18next. Language selection via dropdown in header with automatic persistence to localStorage.

## Dependencies Installed
```bash
yarn add i18next react-i18next i18next-browser-languagedetector
yarn run shadcn add dropdown-menu
```

## File Structure

### Translation Files
```
static/i18n/
├── EN/
│   └── common.json  (English translations - complete)
├── JP/
│   └── common.json  (Japanese - skeleton)
├── KR/
│   └── common.json  (Korean - skeleton)
└── CN/
    └── common.json  (Chinese - skeleton)
```

### Core Implementation Files
- `frontend/src/lib/i18n.ts` - i18next configuration
- `frontend/src/components/LanguageSync.tsx` - HTML lang attribute sync
- `frontend/src/components/GlobalLayout.tsx` - Integration point
- `frontend/src/components/Header.tsx` - Language dropdown
- `frontend/src/main.tsx` - i18n initialization

### Modified Route Files
- `frontend/src/routes/InfoPage.tsx`
- `frontend/src/routes/PlannerPage.tsx`
- `frontend/src/routes/CommunityPage.tsx`

## Translation Key Structure

### Header Navigation
```json
{
  "header": {
    "nav": {
      "info": "In-Game Info",
      "planner": "Planner",
      "community": "Community"
    }
  }
}
```

**Usage locations:**
- [Header.tsx:57](frontend/src/components/Header.tsx#L57) - Info nav link
- [Header.tsx:60](frontend/src/components/Header.tsx#L60) - Planner nav link
- [Header.tsx:63](frontend/src/components/Header.tsx#L63) - Community nav link

### Header Settings
```json
{
  "header": {
    "settings": {
      "language": "Select language",
      "theme": "Toggle theme",
      "settings": "Settings",
      "signIn": "Sign in"
    }
  }
}
```

**Usage locations:**
- [Header.tsx:74](frontend/src/components/Header.tsx#L74) - Language dropdown button aria-label
- [Header.tsx:105](frontend/src/components/Header.tsx#L105) - Theme toggle button aria-label
- [Header.tsx:122](frontend/src/components/Header.tsx#L122) - Settings button aria-label
- [Header.tsx:129](frontend/src/components/Header.tsx#L129) - Sign in button

### Info Page
```json
{
  "pages": {
    "info": {
      "title": "In-Game Info",
      "description": "Placeholder page for game information (Identities, EGOs, EGO Gifts).",
      "backHome": "Back to Home"
    }
  }
}
```

**Usage locations:**
- [InfoPage.tsx:10](frontend/src/routes/InfoPage.tsx#L10) - Page heading
- [InfoPage.tsx:12](frontend/src/routes/InfoPage.tsx#L12) - Description text
- [InfoPage.tsx:15](frontend/src/routes/InfoPage.tsx#L15) - Back button

### Planner Page
```json
{
  "pages": {
    "planner": {
      "title": "Planner",
      "description": "Placeholder page for Mirror Dungeon run planner.",
      "backHome": "Back to Home"
    }
  }
}
```

**Usage locations:**
- [PlannerPage.tsx:10](frontend/src/routes/PlannerPage.tsx#L10) - Page heading
- [PlannerPage.tsx:12](frontend/src/routes/PlannerPage.tsx#L12) - Description text
- [PlannerPage.tsx:15](frontend/src/routes/PlannerPage.tsx#L15) - Back button

### Community Page
```json
{
  "pages": {
    "community": {
      "title": "Community",
      "description": "Placeholder page for community features (share plans, discuss strategies).",
      "backHome": "Back to Home"
    }
  }
}
```

**Usage locations:**
- [CommunityPage.tsx:10](frontend/src/routes/CommunityPage.tsx#L10) - Page heading
- [CommunityPage.tsx:12](frontend/src/routes/CommunityPage.tsx#L12) - Description text
- [CommunityPage.tsx:15](frontend/src/routes/CommunityPage.tsx#L15) - Back button

## Configuration Details

### i18n Setup ([lib/i18n.ts](frontend/src/lib/i18n.ts))
```typescript
// Resources loaded via direct JSON imports
const resources = {
  EN: { common: enCommon },
  JP: { common: jpCommon },
  KR: { common: krCommon },
  CN: { common: cnCommon },
}

// Configuration
i18n.init({
  resources,
  fallbackLng: 'EN',                    // Default language
  supportedLngs: ['EN', 'JP', 'KR', 'CN'],
  ns: ['common'],                        // Namespaces
  defaultNS: 'common',
  detection: {
    order: ['localStorage', 'navigator'], // Detection priority
    caches: ['localStorage'],             // Persistence
    lookupLocalStorage: 'i18nextLng',    // Storage key
  },
  interpolation: {
    escapeValue: false,                   // React handles escaping
  },
  react: {
    useSuspense: false,                   // No suspense mode
  },
})
```

### Language Dropdown ([Header.tsx](frontend/src/components/Header.tsx))
```typescript
const { t, i18n } = useTranslation()

const changeLanguage = (lang: string) => {
  i18n.changeLanguage(lang)
}

<DropdownMenu>
  <DropdownMenuTrigger asChild>
    <Button variant="ghost" size="icon" aria-label={t('header.settings.language')}>
      <Languages />
    </Button>
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end">
    <DropdownMenuRadioGroup value={i18n.language} onValueChange={changeLanguage}>
      <DropdownMenuRadioItem value="EN">English</DropdownMenuRadioItem>
      <DropdownMenuRadioItem value="JP">日本語</DropdownMenuRadioItem>
      <DropdownMenuRadioItem value="KR">한국어</DropdownMenuRadioItem>
      <DropdownMenuRadioItem value="CN">中文</DropdownMenuRadioItem>
    </DropdownMenuRadioGroup>
  </DropdownMenuContent>
</DropdownMenu>
```

**Features:**
- DropdownMenuRadioGroup for single selection
- Visual indicator for current language
- Language names in native scripts
- Aligned to button using align="end"

### HTML Lang Attribute Sync ([LanguageSync.tsx](frontend/src/components/LanguageSync.tsx))
```typescript
export function LanguageSync() {
  const { i18n } = useTranslation()

  useEffect(() => {
    document.documentElement.lang = i18n.language
  }, [i18n.language])

  return null
}
```

**Integration:** Added to [GlobalLayout.tsx:12](frontend/src/components/GlobalLayout.tsx#L12)

## Adding New Translation Keys

### 1. Add to EN Translation File
Edit `static/i18n/EN/common.json`:
```json
{
  "section": {
    "subsection": {
      "key": "English text here"
    }
  }
}
```

### 2. Add Skeletons to Other Languages
Copy structure to JP/KR/CN common.json with empty strings:
```json
{
  "section": {
    "subsection": {
      "key": ""
    }
  }
}
```

### 3. Use in Components
```typescript
import { useTranslation } from 'react-i18next'

export default function Component() {
  const { t } = useTranslation()

  return <div>{t('section.subsection.key')}</div>
}
```

## Language Detection & Persistence

### Detection Order
1. **localStorage** - Checks for saved language preference (key: `i18nextLng`)
2. **navigator** - Falls back to browser language setting

### Persistence
Language changes automatically save to localStorage. No manual save required.

### First Visit Behavior
- If browser language matches supported language → Use that language
- Otherwise → Use fallback language (EN)

## Non-Translated Elements

**"Dante's Planner" title** remains hardcoded in English per requirements.
Location: [Header.tsx:42](frontend/src/components/Header.tsx#L42)

## Testing

### Manual Testing Steps
1. Open http://localhost:5173/
2. Click Languages button in header
3. Select different language from dropdown
4. Verify text changes throughout app
5. Refresh page - verify language persists
6. Check browser DevTools:
   - localStorage should contain `i18nextLng` key
   - HTML tag should have correct `lang` attribute

### Verification Points
- [ ] Language dropdown shows current selection
- [ ] All text except "Dante's Planner" translates
- [ ] Language persists after page refresh
- [ ] HTML lang attribute updates dynamically
- [ ] Fallback to EN for missing translations

## Future Expansion

### Adding New Languages
1. Add language code to `supportedLngs` in [lib/i18n.ts:24](frontend/src/lib/i18n.ts#L24)
2. Create `static/i18n/{CODE}/common.json`
3. Import in [lib/i18n.ts:6-9](frontend/src/lib/i18n.ts#L6-L9)
4. Add resource to resources object
5. Add menu item in [Header.tsx:82-93](frontend/src/components/Header.tsx#L82-L93)

### Adding New Namespaces
1. Create new JSON files: `static/i18n/{LANG}/{namespace}.json`
2. Import in lib/i18n.ts
3. Add to resources structure
4. Add to `ns` array in config
5. Use with: `t('key', { ns: 'namespace' })`

## Common Pitfalls

### 1. shadcn Component Installation
**Wrong:** `yarn shadcn@latest add component-name`
**Correct:** `yarn run shadcn add component-name`

### 2. Missing Read Before Edit
Always read file before editing existing files. Tool will reject edits without prior read.

### 3. Translation Key Typos
Typos in translation keys fail silently and return the key itself. Double-check key paths match JSON structure.

### 4. Forgetting Fallback Content
Always provide English (EN) translations. Other languages can be empty strings for user to fill.

## Implementation Summary

**Total files created:** 9
- 4 translation JSON files
- 1 i18n configuration
- 1 LanguageSync component
- 3 skeleton translation files

**Total files modified:** 6
- main.tsx (i18n import)
- GlobalLayout.tsx (LanguageSync integration)
- Header.tsx (complete rewrite with dropdown)
- InfoPage.tsx (add translations)
- PlannerPage.tsx (add translations)
- CommunityPage.tsx (add translations)

**Lines of code:** ~350 total implementation

## Translation Completion Status

- **EN (English):** ✅ Complete (15 keys)
- **JP (Japanese):** ⏳ Skeleton only - User to fill
- **KR (Korean):** ⏳ Skeleton only - User to fill
- **CN (Chinese):** ⏳ Skeleton only - User to fill
