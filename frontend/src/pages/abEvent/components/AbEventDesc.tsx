import { useTranslation } from 'react-i18next'
import { useAbEventListI18n } from '../hooks/useAbEventListData'
import { KoreanText } from '@/components/ui/KoreanText'

interface AbEventDescProps {
  /** Event ID to look up the description */
  id: string
}

/**
 * Fetches and displays an abnormality event's description.
 * Suspends while the description list loads - requires a Suspense boundary above.
 *
 * Renders an empty string for an id the active language has no description for.
 */
export const AbEventDesc = function AbEventDesc({ id }: AbEventDescProps) {
  const { i18n } = useTranslation()
  const descs = useAbEventListI18n()
  const desc = descs[id] || ''

  if (i18n.language === 'KR') {
    return <KoreanText>{desc}</KoreanText>
  }

  return <>{desc}</>
}
