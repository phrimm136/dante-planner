import { useTranslation } from 'react-i18next'
import { useEGOGiftListI18n } from '@/hooks/useEGOGiftListData'
import { KoreanText } from '@/components/KoreanText'

interface EGOGiftNameProps {
  /** EGO Gift ID to look up name */
  id: string
}

/**
 * Component that fetches and displays EGO Gift name.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * This allows granular loading: card images stay visible while only
 * the name text shows skeleton during language change.
 *
 * For Korean text, uses KoreanText component to handle S-Core Dream's
 * incomplete glyph coverage with Pretendard fallback.
 *
 * @example
 * <Suspense fallback={<Skeleton className="w-16 h-4" />}>
 *   <EGOGiftName id={gift.id} />
 * </Suspense>
 */
export function EGOGiftName({ id }: EGOGiftNameProps) {
  const { i18n } = useTranslation()
  const names = useEGOGiftListI18n()
  const name = names[id] || id

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
}
