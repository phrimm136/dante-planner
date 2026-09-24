import { Suspense, useState } from 'react'

import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { getBattleSpritePath } from '@/shared/assets'
import type { EntityListDataConfig } from '@/shared/entityCatalog'
import type { BattleFamily } from '../lib/battleVocabulary'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { BattleEntityName } from './BattleEntityName'

interface BattleCardProps<TSpecList> {
  family: BattleFamily
  id: string
  spriteId: string
  listConfig: EntityListDataConfig<TSpecList, BattleNameList>
  enableHoverHighlight?: boolean
}

export function BattleCard<TSpecList>({
  family,
  id,
  spriteId,
  listConfig,
  enableHoverHighlight = false,
}: BattleCardProps<TSpecList>) {
  const [spriteMissing, setSpriteMissing] = useState(false)

  return (
    <div data-testid={`battle-card-${id}`} className="group relative flex h-full flex-col gap-1.5">
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
            className="h-full w-full object-contain"
          />
        )}
      </div>

      <div
        data-testid="battle-card-name"
        className="truncate px-1 text-center text-xs leading-tight"
      >
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>
          <BattleEntityName listConfig={listConfig} id={id} />
        </Suspense>
      </div>
    </div>
  )
}
