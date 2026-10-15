import { Suspense, useState } from 'react'
import { Link } from '@tanstack/react-router'

import { Skeleton } from '@/components/ui/skeleton'
import { LIST_CARD_HOVER_CLASS } from '@/lib/constants'
import { cn } from '@/lib/utils'
import { getBattleListImagePath } from '@/shared/assets'
import { BATTLE_DETAIL_ROUTES, type BattleCase, type BattleFamily } from '../lib/battleVocabulary'
import { BattleEntityName } from './BattleEntityName'

interface BattleCardProps {
  family: BattleFamily
  id: string
  mode: BattleCase
  identityTitle: string | undefined
  enableHoverHighlight?: boolean
  enableDetailLink?: boolean
}

export function BattleCard({
  family,
  id,
  mode,
  identityTitle,
  enableHoverHighlight = false,
  enableDetailLink = false,
}: BattleCardProps) {
  const [spriteMissing, setSpriteMissing] = useState(false)

  const content = (
    <>
      <div
        data-testid="battle-card-sprite"
        className={cn(
          'relative min-h-0 w-full flex-1 overflow-hidden rounded-sm bg-muted',
          enableHoverHighlight && LIST_CARD_HOVER_CLASS,
        )}
      >
        {spriteMissing ? (
          <div className="flex h-full w-full items-center justify-center text-xs text-muted-foreground">
            {id}
          </div>
        ) : (
          <img
            src={getBattleListImagePath(family, id)}
            alt=""
            loading="lazy"
            onError={() => {
              setSpriteMissing(true)
            }}
            className="h-full w-full object-cover"
          />
        )}
      </div>

      <div
        data-testid="battle-card-name"
        className="truncate px-1 text-center text-xs leading-tight"
      >
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>
          <BattleEntityName family={family} id={id} mode={mode} identityTitle={identityTitle} />
        </Suspense>
      </div>
    </>
  )

  const testId = `battle-card-${id}`
  const className = 'group relative flex h-full flex-col gap-1.5'

  return enableDetailLink ? (
    <Link
      to={BATTLE_DETAIL_ROUTES[family]}
      params={{ id }}
      data-testid={testId}
      className={className}
    >
      {content}
    </Link>
  ) : (
    <div data-testid={testId} className={className}>
      {content}
    </div>
  )
}
