import { useIdentityDetailI18n } from '@/hooks/useIdentityDetailData'
import { IdentityHeader } from './IdentityHeader'

interface IdentityHeaderI18nProps {
  /** Identity ID to look up name */
  id: string
}

interface IdentityHeaderWithI18nProps {
  /** Identity ID for i18n lookup */
  id: string
  /** Identity rank (1-3) */
  rank: number
  /** Current uptie level (1-4) */
  uptie: number
}

/**
 * Header with i18n name - suspends for language change.
 * Wraps IdentityHeader with i18n data fetching.
 * MUST be wrapped in Suspense boundary.
 *
 * @example
 * <Suspense fallback={<IdentityHeader identityId={id} name="" rank={rank} uptie={uptie} />}>
 *   <IdentityHeaderWithI18n id={id} rank={rank} uptie={uptie} />
 * </Suspense>
 */
export function IdentityHeaderWithI18n({ id, rank, uptie }: IdentityHeaderWithI18nProps) {
  const i18n = useIdentityDetailI18n(id)
  return (
    <IdentityHeader
      identityId={id}
      name={i18n.name}
      rank={rank}
      uptie={uptie}
    />
  )
}

/**
 * Suspending component that fetches Identity name from detail i18n.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * Returns just the name string - caller handles styling.
 * This allows granular loading: header layout stays visible while only
 * the name text shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="h-8 w-48" />}>
 *   <IdentityHeaderI18n id={identity.id} />
 * </Suspense>
 */
export function IdentityHeaderI18n({ id }: IdentityHeaderI18nProps) {
  const i18n = useIdentityDetailI18n(id)

  // Render \n as line breaks for multi-line identity names
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
