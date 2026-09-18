import { Suspense } from 'react'

import { EntityMetaInfoWithI18n } from '@/components/layout/EntityMetaInfoI18n'
import { KeywordsDisplay } from '@/shared/gameText'
import { EGOHeader } from './EGOHeader'
import { EGOHeaderWithI18n } from './EGOHeaderI18n'
import { SinCostPanel } from './SinCostPanel'
import { SinResistancePanel } from './SinResistancePanel'

import type { EGOData, EgoSkillType } from '../types/EGOTypes'
import type { EGOId } from '@/shared/gameData'

interface EGOInfoPaneProps {
  id: EGOId
  ego: EGOData
  skillType: EgoSkillType
}

export function EGOInfoPane({ id, ego, skillType }: EGOInfoPaneProps) {
  return (
    <>
      <div className="space-y-4">
        <Suspense
          fallback={<EGOHeader egoId={id} name="" rank={ego.egoType} skillType={skillType} />}
        >
          <EGOHeaderWithI18n id={id} rank={ego.egoType} skillType={skillType} />
        </Suspense>

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
    </>
  )
}
