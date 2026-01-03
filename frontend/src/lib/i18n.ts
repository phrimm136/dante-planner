import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

// Import translation files directly
import enCommon from '@static/i18n/EN/common.json'
import jpCommon from '@static/i18n/JP/common.json'
import krCommon from '@static/i18n/KR/common.json'
import cnCommon from '@static/i18n/CN/common.json'
import enExtraction from '@static/i18n/EN/extraction.json'
import jpExtraction from '@static/i18n/JP/extraction.json'
import krExtraction from '@static/i18n/KR/extraction.json'
import cnExtraction from '@static/i18n/CN/extraction.json'

const resources = {
  EN: { common: enCommon, extraction: enExtraction },
  JP: { common: jpCommon, extraction: jpExtraction },
  KR: { common: krCommon, extraction: krExtraction },
  CN: { common: cnCommon, extraction: cnExtraction },
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources,
    fallbackLng: 'EN',
    supportedLngs: ['EN', 'JP', 'KR', 'CN'],
    ns: ['common', 'extraction'],
    defaultNS: 'common',

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
