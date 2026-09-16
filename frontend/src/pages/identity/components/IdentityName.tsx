import { useTranslation } from 'react-i18next'
import { useIdentityListI18n } from '../hooks/useIdentityListData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { fontTableLanguage, nameShadow, useFontAdvances } from '@/shared/cardLayout'
import { identityNameLines, nameLineStyle, nameTextStyle } from '../lib/cardLayout'
import type { IdentityId } from '@/shared/gameData'

interface IdentityNameProps {
  /** Identity ID to look up name */
  id: IdentityId
}

/**
 * An identity name, broken into lines by the display face's own advance table.
 *
 * Reads i18n and that table through `useSuspenseQuery` — render it inside a Suspense
 * boundary.
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
  const fontTable = useFontAdvances(i18n.language)
  const lines = identityNameLines(name, fontTable)

  return (
    <span
      className="text-identity-name"
      style={{
        ...nameTextStyle(fontTable, fontTableLanguage(i18n.language)),
        ...getDisplayFontForLanguage(i18n.language),
        textShadow: nameShadow('identity', i18n.language),
      }}
    >
      {lines.map((line, index) => (
        <span
          key={`${String(index)}:${line}`}
          data-testid="identity-name-line"
          style={nameLineStyle()}
        >
          {line}
        </span>
      ))}
    </span>
  )
}
