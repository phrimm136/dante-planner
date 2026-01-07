import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// Import translation files directly
import enCommon from '@static/i18n/EN/common.json'
import jpCommon from '@static/i18n/JP/common.json'
import krCommon from '@static/i18n/KR/common.json'
import cnCommon from '@static/i18n/CN/common.json'
import enDatabase from '@static/i18n/EN/database.json'
import jpDatabase from '@static/i18n/JP/database.json'
import krDatabase from '@static/i18n/KR/database.json'
import cnDatabase from '@static/i18n/CN/database.json'
import enPlanner from '@static/i18n/EN/planner.json'
import jpPlanner from '@static/i18n/JP/planner.json'
import krPlanner from '@static/i18n/KR/planner.json'
import cnPlanner from '@static/i18n/CN/planner.json'
import enExtraction from '@static/i18n/EN/extraction.json'
import jpExtraction from '@static/i18n/JP/extraction.json'
import krExtraction from '@static/i18n/KR/extraction.json'
import cnExtraction from '@static/i18n/CN/extraction.json'

const resources = {
  EN: { common: enCommon, database: enDatabase, planner: enPlanner, extraction: enExtraction },
  JP: { common: jpCommon, database: jpDatabase, planner: jpPlanner, extraction: jpExtraction },
  KR: { common: krCommon, database: krDatabase, planner: krPlanner, extraction: krExtraction },
  CN: { common: cnCommon, database: cnDatabase, planner: cnPlanner, extraction: cnExtraction },
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'EN',
    supportedLngs: ['EN', 'JP', 'KR', 'CN'],
    ns: ['common', 'database', 'planner', 'extraction'],
    defaultNS: 'common',
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
    },
  })

export default i18n
