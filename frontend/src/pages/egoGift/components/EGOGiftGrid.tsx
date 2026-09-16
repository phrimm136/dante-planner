import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'

import { Skeleton } from '@/components/ui/skeleton'
import { CARD_MOBILE_SCALE_NONE, SECTION_STYLES } from '@/lib/constants'
import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'

import { toEGOGiftCardProps } from '../lib/egoGiftCardProps'
import type { EGOGiftSpec } from '../types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftName } from './EGOGiftName'

const NAME_CLASS =
  'text-xs text-center text-foreground line-clamp-2 w-full leading-tight font-medium'

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

        const card = (
          <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={CARD_MOBILE_SCALE_NONE}>
            <EGOGiftCard gift={toEGOGiftCardProps(id, giftSpec)} enableHoverHighlight />
          </CardSlot>
        )

        return (
          <Link key={id} to="/ego-gift/$id" params={{ id }}>
            {showName ? (
              <div className="flex flex-col items-center gap-1">
                {card}
                <span className={NAME_CLASS}>
                  <Suspense fallback={<Skeleton className="h-5 w-full" />}>
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
