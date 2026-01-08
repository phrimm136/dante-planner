import { Suspense } from 'react'
import { useEGODetailI18n } from '@/hooks/useEGODetailData'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { StyledSkillName, StyledNameSkeleton } from '@/components/common/StyledName'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

interface PassiveCardWithSuspenseProps {
  /** EGO ID for i18n lookup */
  id: string
  /** Passive ID (string type for EGO) */
  passiveId: string
  /** Whether this passive is locked (from higher threadspin) */
  isLocked: boolean
}

/**
 * EGO Passive card with granular i18n Suspense.
 * Structure (locked indicator) stays visible,
 * only name and description suspend.
 */
export function PassiveCardWithSuspense({
  id,
  passiveId,
  isLocked,
}: PassiveCardWithSuspenseProps) {
  return (
    <div className={cn('space-y-1', isLocked && 'opacity-50')}>
      <div className="flex items-center gap-2">
        <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" />}>
          <PassiveNameContent id={id} passiveId={passiveId} />
        </Suspense>
        {isLocked && <span className="text-xs">🔒</span>}
      </div>
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>
          <PassiveDescContent id={id} passiveId={passiveId} />
        </Suspense>
      </div>
    </div>
  )
}

/**
 * Internal: Fetches and renders passive name with styled formatting.
 */
function PassiveNameContent({ id, passiveId }: { id: string; passiveId: string }) {
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  return <StyledSkillName name={passive?.name || passiveId} attributeType="NEUTRAL" />
}

/**
 * Internal: Fetches and renders passive description with keyword formatting.
 */
function PassiveDescContent({ id, passiveId }: { id: string; passiveId: string }) {
  const i18n = useEGODetailI18n(id)
  const passive = i18n.passives[passiveId]
  return <FormattedDescription text={passive?.desc ?? ''} />
}

/**
 * Alias for PassiveCardWithSuspense.
 * Used in EGODetailPage.tsx - kept for semantic clarity.
 */
export { PassiveCardWithSuspense as PassiveCardI18n }
