import { useEGOListI18n } from '@/hooks/useEGOListData'

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
export function EGOName({ id }: EGONameProps) {
  const i18n = useEGOListI18n()
  return <>{i18n[id] || id}</>
}
