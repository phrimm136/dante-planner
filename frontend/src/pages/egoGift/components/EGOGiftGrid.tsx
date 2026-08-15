import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'

import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'

import { toEGOGiftCardProps } from '../lib/egoGiftCardProps'
import type { EGOGiftSpec } from '../types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'

const NAME_CLASS = 'text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium'

interface EGOGiftGridProps {
  /** Gift ids in render order; ids missing from `spec` are skipped. */
  ids: readonly string[]
  spec: Record<string, EGOGiftSpec>
  /** Show the gift name under each card. */
  showName?: boolean
  /** Wrapper classes for the row. */
  className?: string
}

/** Wrapping row of EGO gift cards, each linking to its detail page. */
export function EGOGiftGrid({
  ids,
  spec,
  showName = false,
  className = SECTION_STYLES.LAYOUT.wrap,
}: EGOGiftGridProps) {
  return (
    <div className={className}>
      {ids.map((id) => {
        const giftSpec = spec[id]
        if (!giftSpec) return null

        const card = <EGOGiftCard gift={toEGOGiftCardProps(id, giftSpec)} enableHoverHighlight />

        return (
          <Link key={id} to="/ego-gift/$id" params={{ id }}>
            {showName ? (
              <div className="flex flex-col items-center gap-1">
                {card}
                <span className={NAME_CLASS}>
                  <Suspense fallback={<Skeleton className="h-5 w-24 bg-foreground" />}>
                    <EGOGiftName id={id} />
                  </Suspense>
                </span>
              </div>
            ) : (
              card
            )}
          </Link>
        )
      })}
    </div>
  )
}
