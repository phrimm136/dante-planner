import { Suspense } from 'react'

import { EntityMetaInfoWithI18n } from '@/components/layout/EntityMetaInfoI18n'
import { EGOHeader } from './EGOHeader'
import { EGOHeaderWithI18n } from './EGOHeaderI18n'
import { SinCostPanel } from './SinCostPanel'
import { SinResistancePanel } from './SinResistancePanel'

import type { EGOData } from '../types/EGOTypes'

interface EGOInfoPaneProps {
  /** EGO ID for i18n lookup */
  id: string
  /** EGO spec data */
  ego: EGOData
}

/**
 * Left pane of the EGO detail page: header, sin cost, sin resistance and metadata.
 *
 * Reads nothing the right pane owns, so a skill-type switch leaves it untouched.
 */
export function EGOInfoPane({ id, ego }: EGOInfoPaneProps) {
  return (
    <>
      <div className="space-y-4">
        {/* Header with rank, name, and image - Suspends for i18n name */}
        <Suspense fallback={<EGOHeader egoId={id} name="" rank={ego.egoType} />}>
          <EGOHeaderWithI18n id={id} rank={ego.egoType} />
        </Suspense>

        {/* Sin Cost and Sin Resistance Panels */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <SinCostPanel costs={ego.requirements} />
          <SinResistancePanel resistances={ego.attributeResist} />
        </div>

        {/* Season and Release Date - Suspense for i18n data */}
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
