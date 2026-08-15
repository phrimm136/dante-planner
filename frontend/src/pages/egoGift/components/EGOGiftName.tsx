import { useTranslation } from 'react-i18next'
import { useEGOGiftListI18n } from '../hooks/useEGOGiftListData'
import { KoreanText } from '@/components/ui/KoreanText'

interface EGOGiftNameProps {
  /** EGO Gift ID to look up name */
  id: string
}

/**
 * Component that fetches and displays EGO Gift name.
 * Suspends while the name list loads - requires a Suspense boundary above.
 * Memoized by id to prevent re-renders during list filtering.
 *
 * Renders an empty string for an id the active language has no name for.
 *
 * For Korean text, uses KoreanText component to handle S-Core Dream's
 * incomplete glyph coverage with Pretendard fallback.
 *
 * @example
 * <EGOGiftName id={gift.id} />
 */
export const EGOGiftName = function EGOGiftName({ id }: EGOGiftNameProps) {
  const { i18n } = useTranslation()
  const names = useEGOGiftListI18n()
  const name = names[id] || ''

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
}
