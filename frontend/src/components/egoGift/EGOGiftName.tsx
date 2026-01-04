import { useEGOGiftListI18n } from '@/hooks/useEGOGiftListData'

interface EGOGiftNameProps {
  /** EGO Gift ID to look up name */
  id: string
}

/**
 * Component that fetches and displays EGO Gift name.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * This allows granular loading: card images stay visible while only
 * the name text shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="w-16 h-4" />}>
 *   <EGOGiftName id={gift.id} />
 * </Suspense>
 */
export function EGOGiftName({ id }: EGOGiftNameProps) {
  const i18n = useEGOGiftListI18n()
  return <>{i18n[id] || id}</>
}
