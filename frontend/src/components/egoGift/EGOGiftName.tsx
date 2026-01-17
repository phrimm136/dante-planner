import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListI18nDeferred } from '@/hooks/useEGOGiftListData'
import { KoreanText } from '@/components/KoreanText'

interface EGOGiftNameProps {
  /** EGO Gift ID to look up name */
  id: string
}

/**
 * Component that fetches and displays EGO Gift name.
 * Uses non-suspending hook - does NOT require Suspense boundary.
 * Memoized by id to prevent re-renders during list filtering.
 *
 * Returns empty string while loading (caller can show fallback if needed).
 *
 * For Korean text, uses KoreanText component to handle S-Core Dream's
 * incomplete glyph coverage with Pretendard fallback.
 *
 * @example
 * <EGOGiftName id={gift.id} />
 */
export const EGOGiftName = memo(function EGOGiftName({ id }: EGOGiftNameProps) {
  const { i18n } = useTranslation()
  const names = useEGOGiftListI18nDeferred()
  const name = names[id] || ''

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
})
