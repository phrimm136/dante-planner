import { useIdentityListI18n } from '@/hooks/useIdentityListData'

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
  const i18n = useIdentityListI18n()
  const name = i18n[id] || id

  // Render \n as line breaks for multi-line identity names
  const lines = name.split('\n')
  if (lines.length === 1) {
    return <>{name}</>
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
