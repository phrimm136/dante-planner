import { Suspense, useState } from 'react'
import { Link } from '@tanstack/react-router'

import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { getBattleSpritePath } from '@/shared/assets'
import type { EntityListDataConfig } from '@/shared/entityCatalog'
import { spritePlacementStyle } from '../lib/battleListing'
import { BATTLE_DETAIL_ROUTES, type BattleCase, type BattleFamily } from '../lib/battleVocabulary'
import type { BattleNameList, BattleSprite } from '../schemas/BattleSpecSchemas'
import { BattleEntityName } from './BattleEntityName'

interface BattleCardProps<TSpecList> {
  family: BattleFamily
  id: string
  spriteId: string
  sprite: BattleSprite
  mode: BattleCase
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
  enableHoverHighlight?: boolean
  enableDetailLink?: boolean
}

export function BattleCard<TSpecList>({
  family,
  id,
  spriteId,
  sprite,
  mode,
  listConfig,
  enableHoverHighlight = false,
  enableDetailLink = false,
}: BattleCardProps<TSpecList>) {
  const [spriteMissing, setSpriteMissing] = useState(false)
  const placement = spritePlacementStyle(sprite)

  const content = (
    <>
      <div
        data-testid="battle-card-sprite"
        className={cn(
          'relative min-h-0 w-full flex-1 overflow-hidden rounded-sm bg-muted',
          enableHoverHighlight && 'selectable [--selectable-transition-duration:0ms]',
        )}
      >
        {spriteMissing ? (
          <div className="flex h-full w-full items-center justify-center text-xs text-muted-foreground">
            {id}
          </div>
        ) : (
          <img
            src={getBattleSpritePath(family, spriteId)}
            alt=""
            loading="lazy"
            onError={() => {
              setSpriteMissing(true)
            }}
            style={placement}
            className={placement === undefined ? 'h-full w-full object-contain' : undefined}
          />
        )}
      </div>

      <div
        data-testid="battle-card-name"
        className="truncate px-1 text-center text-xs leading-tight"
      >
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>
          <BattleEntityName listConfig={listConfig} id={id} mode={mode} />
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
