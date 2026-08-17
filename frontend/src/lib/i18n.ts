import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// EN is the fallback language, so it is needed on every load and stays bundled.
import enCommon from '@static/i18n/EN/common.json'
import enDatabase from '@static/i18n/EN/database.json'
import enPlanner from '@static/i18n/EN/planner.json'
import enModeration from '@static/i18n/EN/moderation.json'
import enExtraction from '@static/i18n/EN/extraction.json'
import enEpithet from '@static/i18n/EN/epithet.json'
import enSinnerNames from '@static/i18n/EN/sinnerNames.json'

const NAMESPACES = [
  'common',
  'database',
  'planner',
  'extraction',
  'epithet',
  'sinnerNames',
  'moderation',
] as const

const resources = {
  EN: {
    common: enCommon,
    database: enDatabase,
    planner: enPlanner,
    extraction: enExtraction,
    epithet: enEpithet,
    sinnerNames: enSinnerNames,
    moderation: enModeration,
  },
}

/**
 * Every importer is a literal `import('@static/…')` so the build can split each
 * namespace into its own chunk; a variable path would defeat that.
 */
type Namespace = (typeof NAMESPACES)[number]

const translationChunks: Record<string, Record<Namespace, () => Promise<{ default: unknown }>>> = {
  JP: {
    common: () => import('@static/i18n/JP/common.json'),
    database: () => import('@static/i18n/JP/database.json'),
    planner: () => import('@static/i18n/JP/planner.json'),
    extraction: () => import('@static/i18n/JP/extraction.json'),
    epithet: () => import('@static/i18n/JP/epithet.json'),
    sinnerNames: () => import('@static/i18n/JP/sinnerNames.json'),
    moderation: () => import('@static/i18n/JP/moderation.json'),
  },
  KR: {
    common: () => import('@static/i18n/KR/common.json'),
    database: () => import('@static/i18n/KR/database.json'),
    planner: () => import('@static/i18n/KR/planner.json'),
    extraction: () => import('@static/i18n/KR/extraction.json'),
    epithet: () => import('@static/i18n/KR/epithet.json'),
    sinnerNames: () => import('@static/i18n/KR/sinnerNames.json'),
    moderation: () => import('@static/i18n/KR/moderation.json'),
  },
  CN: {
    common: () => import('@static/i18n/CN/common.json'),
    database: () => import('@static/i18n/CN/database.json'),
    planner: () => import('@static/i18n/CN/planner.json'),
    extraction: () => import('@static/i18n/CN/extraction.json'),
    epithet: () => import('@static/i18n/CN/epithet.json'),
    sinnerNames: () => import('@static/i18n/CN/sinnerNames.json'),
    moderation: () => import('@static/i18n/CN/moderation.json'),
  },
}

const loaded = new Set<string>(['EN'])

/** Adds every namespace of `language` to the i18next store, once. */
async function loadLanguage(language: string | undefined): Promise<void> {
  if (!language || loaded.has(language)) return
  const chunks = translationChunks[language]
  if (!chunks) return
  loaded.add(language)
  try {
    const bundles = await Promise.all(
      NAMESPACES.map(async (ns) => ({ ns, resource: (await chunks[ns]()).default })),
    )
    for (const { ns, resource } of bundles) {
      i18n.addResourceBundle(language, ns, resource, true, true)
    }
  } catch (error) {
    // A failed fetch must not keep the app from rendering: EN is already in the
    // store, so the UI degrades to the fallback language instead of to nothing.
    loaded.delete(language)
    console.error(`[i18n] failed to load ${language} translations`, error)
  }
}

/**
 * The language whose bundle has to be fetched. `resolvedLanguage` cannot serve
 * here: until the bundle lands i18next resolves past the requested language to
 * the bundled EN fallback, which would keep the fetch from ever happening.
 */
function requestedLanguage(): string | undefined {
  return i18n.languages?.find((language) => language in translationChunks)
}

const initPromise = i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'EN',
    supportedLngs: ['EN', 'JP', 'KR', 'CN'],
    ns: [...NAMESPACES],
    defaultNS: 'common',
    partialBundledLanguages: true,
    // Note: fallbackNS removed intentionally. Components must explicitly declare
    // their namespace dependencies via useTranslation(['namespace', 'common']).

    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'i18nextLng',
    },

    interpolation: {
      escapeValue: false, // React already escapes
    },

    react: {
      useSuspense: false,
      // Re-render once a lazily added language bundle reaches the store.
      bindI18nStore: 'added',
    },
  })

i18n.on('languageChanged', () => {
  void loadLanguage(requestedLanguage())
})

/**
 * Resolves once the active language's translations are in the store. Awaited
 * before the first render so a non-EN visitor never sees English text.
 */
export const i18nReady: Promise<void> = initPromise
  .then(() => loadLanguage(requestedLanguage()))
  .catch((error: unknown) => {
    console.error('[i18n] initialisation failed', error)
  })

export default i18n
