import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { useKeywordListI18nDeferred } from '@/hooks/useKeywordListData'
import { KoreanText } from '@/components/KoreanText'

interface KeywordNameProps {
  /** Keyword ID to look up name */
  id: string
}

/**
 * Component that fetches and displays keyword name.
 * Uses non-suspending hook - does NOT require Suspense boundary.
 * Memoized by id to prevent re-renders during list filtering.
 *
 * Returns empty string while loading (caller can show fallback if needed).
 *
 * For Korean text, uses KoreanText component to handle S-Core Dream's
 * incomplete glyph coverage with Pretendard fallback.
 */
export const KeywordName = memo(function KeywordName({ id }: KeywordNameProps) {
  const { i18n } = useTranslation()
  const names = useKeywordListI18nDeferred()
  const name = names[id]?.name ?? ''

  if (i18n.language === 'KR') {
    return <KoreanText>{name}</KoreanText>
  }

  return <>{name}</>
})
