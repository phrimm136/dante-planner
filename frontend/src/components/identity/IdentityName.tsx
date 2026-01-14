import { useTranslation } from 'react-i18next'
import { useIdentityListI18n } from '@/hooks/useIdentityListData'
import { getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { AutoSizeWrappedText } from '@/components/common/AutoSizeWrappedText'

interface IdentityNameProps {
  /** Identity ID to look up name */
  id: string
}

/**
 * Component that fetches and displays Identity name.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * This allows granular loading: card images stay visible while only
 * the name text shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="w-16 h-4" />}>
 *   <IdentityName id={identity.id} />
 * </Suspense>
 */
export function IdentityName({ id }: IdentityNameProps) {
  const { i18n } = useTranslation()
  const i18nData = useIdentityListI18n()
  const rawName = i18nData[id] || id
  // Replace " - " with non-breaking space before hyphen to prevent orphaned hyphens
  const name = rawName.replace(/ - /g, '\u00A0- ')
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const lineHeight = getLineHeightForLanguage(i18n.language)

  return (
    <AutoSizeWrappedText
      text={name}
      width={127}
      maxLines={5}
      className="text-right leading-4 text-identity-name"
      style={{...displayStyle}}
      minFontSize={8}
      maxFontSize={18}
      lineHeight={lineHeight}
    />
  )
}
