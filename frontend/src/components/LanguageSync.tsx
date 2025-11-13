import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

/**
 * Component to sync HTML lang attribute with i18n language
 */
export function LanguageSync() {
  const { i18n } = useTranslation()

  useEffect(() => {
    document.documentElement.lang = i18n.language
  }, [i18n.language])

  return null
}
