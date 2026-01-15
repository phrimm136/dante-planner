import { useTranslation } from 'react-i18next'
import { useEGOListI18n } from '@/hooks/useEGOListData'
import { getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { AutoSizeWrappedText } from '@/components/common/AutoSizeWrappedText'

interface EGONameProps {
  /** EGO ID to look up name */
  id: string
}

/**
 * Component that fetches and displays EGO name.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * This allows granular loading: card images stay visible while only
 * the name text shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="w-16 h-4" />}>
 *   <EGOName id={ego.id} />
 * </Suspense>
 */
/**
 * Inserts zero-width spaces between CJK ideographs (kanji/hanzi) to enable
 * character-level line breaking while preserving Korean word boundaries.
 * Unicode range U+4E00-U+9FFF covers CJK Unified Ideographs.
 */
function insertKanjiBreaks(text: string): string {
  // Match sequences of CJK ideographs and insert zero-width space between each
  return text.replace(/[\u4E00-\u9FFF]+/g, (match) =>
    match.split('').join('\u200B')
  )
}

export function EGOName({ id }: EGONameProps) {
  const { i18n } = useTranslation()
  const i18nData = useEGOListI18n()
  const rawName = i18nData[id] || id
  // Replace " - " with non-breaking space before hyphen to prevent orphaned hyphens
  // Insert zero-width spaces between kanji for character-level breaking
  const name = insertKanjiBreaks(rawName.replace(/ - /g, '\u00A0- '))
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const lineHeight = getLineHeightForLanguage(i18n.language)

  return (
    <AutoSizeWrappedText
      text={name}
      width={72}
      maxLines={3}
      className="text-center"
      style={{...displayStyle}}
      minFontSize={6}
      maxFontSize={11.2}
      lineHeight={lineHeight}
      wordBreak="keep-all"
    />
  )
}
