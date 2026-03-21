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

import enModeration from '@static/i18n/EN/moderation.json'
import jpModeration from '@static/i18n/JP/moderation.json'
import krModeration from '@static/i18n/KR/moderation.json'
import cnModeration from '@static/i18n/CN/moderation.json'
import enExtraction from '@static/i18n/EN/extraction.json'
import jpExtraction from '@static/i18n/JP/extraction.json'
import krExtraction from '@static/i18n/KR/extraction.json'
import cnExtraction from '@static/i18n/CN/extraction.json'
import enEpithet from '@static/i18n/EN/epithet.json'
import jpEpithet from '@static/i18n/JP/epithet.json'
import krEpithet from '@static/i18n/KR/epithet.json'
import cnEpithet from '@static/i18n/CN/epithet.json'
import enSinnerNames from '@static/i18n/EN/sinnerNames.json'
import jpSinnerNames from '@static/i18n/JP/sinnerNames.json'
import krSinnerNames from '@static/i18n/KR/sinnerNames.json'
import cnSinnerNames from '@static/i18n/CN/sinnerNames.json'

const resources = {
  EN: { common: enCommon, database: enDatabase, planner: enPlanner, extraction: enExtraction, epithet: enEpithet, sinnerNames: enSinnerNames, moderation: enModeration },
  JP: { common: jpCommon, database: jpDatabase, planner: jpPlanner, extraction: jpExtraction, epithet: jpEpithet, sinnerNames: jpSinnerNames, moderation: jpModeration },
  KR: { common: krCommon, database: krDatabase, planner: krPlanner, extraction: krExtraction, epithet: krEpithet, sinnerNames: krSinnerNames, moderation: krModeration },
  CN: { common: cnCommon, database: cnDatabase, planner: cnPlanner, extraction: cnExtraction, epithet: cnEpithet, sinnerNames: cnSinnerNames, moderation: cnModeration },
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'EN',
    supportedLngs: ['EN', 'JP', 'KR', 'CN'],
    ns: ['common', 'database', 'planner', 'extraction', 'epithet', 'sinnerNames', 'moderation'],
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

    showSupportNotice: false,

    react: {
      useSuspense: false,
    },
  })

export default i18n
