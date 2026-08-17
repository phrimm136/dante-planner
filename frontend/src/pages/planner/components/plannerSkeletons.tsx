/**
 * Suspense fallbacks shared by the planner editor and both viewers, so a
 * section's loading shape is described once wherever that section is hosted.
 */

import { Skeleton } from '@/components/ui/skeleton'
import { STAGGER_STEP_MS, SECTION_STYLES } from '@/lib/constants'
import { staggerDelay } from '@/lib/stagger'

import { PlannerSection } from '@/components/layout/PlannerSection'

const SINNER_TILES = 12
const GIFT_TILES = 6
const SKILL_TILES = 12

/** Sinner cards of the deck summary, before the deck data resolves. */
export function DeckGridSkeleton() {
  return (
    <div className="space-y-2">
      <div className="border-2 border-border rounded-lg p-4">
        <div className={SECTION_STYLES.LAYOUT.wrap}>
          {Array.from({ length: SINNER_TILES }).map((_, i) => (
            <Skeleton key={i} className="w-16 h-20 rounded-md" style={staggerDelay(i)} />
          ))}
        </div>
      </div>
    </div>
  )
}

interface SectionBlockSkeletonProps {
  /** Size of the block, which differs per section. */
  className?: string
}

/** A section rendered as one solid block. */
export function SectionBlockSkeleton({
  className = 'h-32 w-full rounded-lg',
}: SectionBlockSkeletonProps) {
  return (
    <div className="space-y-2">
      <Skeleton className={className} />
    </div>
  )
}

interface GiftGridSkeletonProps {
  title: string
  /** The editing surfaces carry a gift-count control above the grid. */
  showCount?: boolean
}

/** Wrapping grid of gift tiles inside its titled section. */
export function GiftGridSkeleton({ title, showCount = false }: GiftGridSkeletonProps) {
  return (
    <PlannerSection title={title}>
      <div className="space-y-4">
        {showCount && (
          <div className="flex justify-end">
            <div className={SECTION_STYLES.LAYOUT.rowTight}>
              <Skeleton className="w-8 h-8 rounded-md" />
              <Skeleton className="w-12 h-6" />
            </div>
          </div>
        )}
        <div className="flex flex-wrap gap-2 p-2 min-h-28">
          {Array.from({ length: GIFT_TILES }).map((_, i) => (
            <Skeleton
              key={i}
              className="w-24 h-24 rounded-md"
              style={staggerDelay(i, STAGGER_STEP_MS.LOOSE)}
            />
          ))}
        </div>
      </div>
    </PlannerSection>
  )
}

/** One card per sinner, each with its three skill slots. */
export function SkillGridSkeleton({ title }: { title: string }) {
  return (
    <PlannerSection title={title}>
      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
        {Array.from({ length: SKILL_TILES }).map((_, i) => (
          <div
            key={i}
            className="flex flex-col items-center gap-1 p-2 rounded-lg border-2 border-border bg-card"
            style={staggerDelay(i, STAGGER_STEP_MS.NORMAL)}
          >
            <Skeleton className="w-24 h-24 rounded-md" />
            <div className="flex gap-1">
              <Skeleton className="w-7 h-7 rounded-sm" />
              <Skeleton className="w-7 h-7 rounded-sm" />
              <Skeleton className="w-7 h-7 rounded-sm" />
            </div>
          </div>
        ))}
      </div>
    </PlannerSection>
  )
}
