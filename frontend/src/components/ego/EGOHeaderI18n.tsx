import { useEGODetailI18n } from '@/hooks/useEGODetailData'
import { EGOHeader } from './EGOHeader'
import type { EGOType } from '@/types/EGOTypes'

interface EGOHeaderI18nProps {
  /** EGO ID to look up name */
  id: string
}

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

/**
 * Suspending component that fetches EGO name from detail i18n.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * Returns just the name string - caller handles styling.
 * This allows granular loading: header layout stays visible while only
 * the name text shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="h-8 w-48" />}>
 *   <EGOHeaderI18n id={ego.id} />
 * </Suspense>
 */
export function EGOHeaderI18n({ id }: EGOHeaderI18nProps) {
  const i18n = useEGODetailI18n(id)

  // Render \n as line breaks for multi-line EGO names
  const lines = i18n.name.split('\n')
  if (lines.length === 1) {
    return <>{i18n.name}</>
  }

  return (
    <>
      {lines.map((line, index) => (
        <span key={`line-${index}`}>
          {line}
          {index < lines.length - 1 && <br />}
        </span>
      ))}
    </>
  )
}
