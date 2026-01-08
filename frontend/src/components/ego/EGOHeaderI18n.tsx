import { useEGODetailI18n } from '@/hooks/useEGODetailData'
import { EGOHeader } from './EGOHeader'
import type { EGOType } from '@/types/EGOTypes'

interface EGOHeaderWithI18nProps {
  /** EGO ID for i18n lookup */
  id: string
  /** EGO rank (ZAYIN, TETH, HE, WAW, ALEPH) */
  rank: EGOType
}

/**
 * Header with i18n name - suspends for language change.
 * Wraps EGOHeader with i18n data fetching.
 * MUST be wrapped in Suspense boundary.
 *
 * @example
 * <Suspense fallback={<EGOHeader egoId={id} name="" rank={rank} />}>
 *   <EGOHeaderWithI18n id={id} rank={rank} />
 * </Suspense>
 */
export function EGOHeaderWithI18n({ id, rank }: EGOHeaderWithI18nProps) {
  const i18n = useEGODetailI18n(id)
  return (
    <EGOHeader
      egoId={id}
      name={i18n.name}
      rank={rank}
    />
  )
}
