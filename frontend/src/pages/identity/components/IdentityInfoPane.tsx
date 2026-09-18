import { Suspense } from 'react'

import { EntityMetaInfoWithI18n } from '@/components/layout/EntityMetaInfoI18n'
import { KeywordsDisplay } from '@/shared/gameText'
import { IdentityHeader } from './IdentityHeader'
import { IdentityHeaderWithI18n } from './IdentityHeaderI18n'
import { StatusPanel } from './StatusPanel'
import { ResistancePanel } from './ResistancePanel'
import { StaggerPanel } from './StaggerPanel'
import { TraitsDisplay } from './TraitsDisplay'

import type { IdentityData, Uptie } from '../types/IdentityTypes'
import type { IdentityId } from '@/shared/gameData'

interface IdentityInfoPaneProps {
  id: IdentityId
  identity: IdentityData
  uptie: Uptie
  level: number
}

export function IdentityInfoPane({ id, identity, uptie, level }: IdentityInfoPaneProps) {
  const calculatedHp = Math.floor(identity.hp.defaultStat + identity.hp.incrementByLevel * level)
  const calculatedDefense = Math.max(1, level + identity.defCorrection)

  const uptieIndex = uptie - 1
  const minSpeed = identity.minSpeedList[uptieIndex] ?? identity.minSpeedList[0]
  const maxSpeed = identity.maxSpeedList[uptieIndex] ?? identity.maxSpeedList[0]
  if (minSpeed === undefined || maxSpeed === undefined) {
    throw new Error(`Identity ${id} has no speed range`)
  }

  return (
    <>
      <div className="space-y-4">
        <Suspense
          fallback={<IdentityHeader identityId={id} name="" rank={identity.rank} uptie={uptie} />}
        >
          <IdentityHeaderWithI18n id={id} rank={identity.rank} uptie={uptie} />
        </Suspense>

        <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
          <StatusPanel
            hp={calculatedHp}
            minSpeed={minSpeed}
            maxSpeed={maxSpeed}
            defLevel={calculatedDefense}
            defCorrection={identity.defCorrection}
          />

          <ResistancePanel
            slash={identity.ResistInfo.SLASH}
            pierce={identity.ResistInfo.PENETRATE}
            blunt={identity.ResistInfo.HIT}
          />

          <div className="col-span-2 md:col-span-1">
            <StaggerPanel maxHP={calculatedHp} staggerThresholds={identity.staggerList} />
          </div>
        </div>

        <TraitsDisplay traits={identity.unitKeywordList} />

        <KeywordsDisplay keywords={identity.battleKeywordList} />

        <Suspense
          fallback={
            <div className="grid grid-cols-2 gap-2">
              <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
              <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
            </div>
          }
        >
          <EntityMetaInfoWithI18n season={identity.season} updateDate={identity.updatedDate} />
        </Suspense>
      </div>
    </>
  )
}
