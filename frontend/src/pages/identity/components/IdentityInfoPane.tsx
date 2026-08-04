import { Suspense } from 'react'

import { EntityMetaInfo } from '@/components/layout/EntityMetaInfo'
import { IdentityHeader } from './IdentityHeader'
import { IdentityHeaderWithI18n } from './IdentityHeaderI18n'
import { StatusPanel } from './StatusPanel'
import { ResistancePanel } from './ResistancePanel'
import { StaggerPanel } from './StaggerPanel'
import { TraitsDisplay } from './TraitsDisplay'

import type { IdentityData, Uptie } from '../types/IdentityTypes'

interface IdentityInfoPaneProps {
  /** Identity ID for i18n lookup */
  id: string
  /** Identity spec data */
  identity: IdentityData
  /** Current uptie level (1-4) */
  uptie: Uptie
  /** Current level (1-MAX_LEVEL) */
  level: number
}

/**
 * Left pane of the identity detail page: header, stats, traits and metadata.
 *
 * Reads uptie and level but owns neither, so a right-pane interaction leaves
 * this subtree untouched.
 */
export function IdentityInfoPane({ id, identity, uptie, level }: IdentityInfoPaneProps) {
  const calculatedHp = Math.floor(identity.hp.defaultStat + identity.hp.incrementByLevel * level)
  const calculatedDefense = Math.max(1, level + identity.defCorrection)

  const uptieIndex = uptie - 1
  const minSpeed = identity.minSpeedList[uptieIndex] ?? identity.minSpeedList[0]
  const maxSpeed = identity.maxSpeedList[uptieIndex] ?? identity.maxSpeedList[0]

  return (
    <>
      {/* Header Area */}
      <div className="space-y-4">
        {/* Header with rank, name, and image - Suspends for i18n name */}
        <Suspense
          fallback={<IdentityHeader identityId={id} name="" rank={identity.rank} uptie={uptie} />}
        >
          <IdentityHeaderWithI18n id={id} rank={identity.rank} uptie={uptie} />
        </Suspense>

        {/* Status, Resistance, and Stagger Panels */}
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

        {/* Traits Panel - Already has granular Suspense internally */}
        <TraitsDisplay traits={identity.unitKeywordList} />

        {/* Season and Release Date - Suspense for i18n data */}
        <Suspense
          fallback={
            <div className="grid grid-cols-2 gap-2">
              <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
              <div className="border rounded p-3 h-16 animate-pulse bg-muted" />
            </div>
          }
        >
          <EntityMetaInfo season={identity.season} updateDate={identity.updatedDate} />
        </Suspense>
      </div>
    </>
  )
}
