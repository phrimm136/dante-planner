import { useTranslation } from 'react-i18next'
import { useIdentityListI18n } from '@/hooks/useIdentityListData'
import { getDisplayFontForLanguage } from '@/lib/utils'

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
  const name = i18nData[id] || id
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  // Render \n as line breaks for multi-line identity names
  const lines = name.split('\n')
  if (lines.length === 1) {
    return <span style={displayStyle}>{name}</span>
  }

  return (
    <span style={displayStyle}>
      {lines.map((line, index) => (
        <span key={`line-${index}`}>
          {line}
          {index < lines.length - 1 && <br />}
        </span>
      ))}
    </span>
  )
}
