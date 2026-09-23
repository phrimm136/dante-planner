import { Suspense } from 'react'

import { EntityMetaInfoWithI18n } from '@/components/layout/EntityMetaInfoI18n'
import { KeywordsDisplay } from '@/shared/gameText'
import { SinCostPanel, SinResistancePanel } from '@/shared/unitStatus'

import type { EGOData } from '../types/EGOTypes'

interface EGOInfoPaneProps {
  ego: EGOData
}

export function EGOInfoPane({ ego }: EGOInfoPaneProps) {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
        <SinCostPanel costs={ego.requirements} />
        <SinResistancePanel resistances={ego.attributeResist} />
      </div>

      <KeywordsDisplay keywords={ego.battleKeywordList} />

      <Suspense
        fallback={
          <div className="grid grid-cols-2 gap-2">
            <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
            <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
          </div>
        }
      >
        <EntityMetaInfoWithI18n season={ego.season} updateDate={ego.updatedDate} />
      </Suspense>
    </div>
  )
}
