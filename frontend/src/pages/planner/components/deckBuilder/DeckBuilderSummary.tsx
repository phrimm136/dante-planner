import { useTranslation } from 'react-i18next'
import { DEFAULT_DEPLOYMENT_MAX, floorCount } from '@/shared/gameData'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { useIdentityListSpec, useIdentityListI18n, toIdentityEntity } from '@/pages/identity'
import { useEGOListSpec } from '@/pages/ego'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { EGOGiftId } from '@/shared/gameData'
import type { SinnerEquipment, DeckState } from '../../types/DeckTypes'
import type { IdentityEntity } from '@/pages/identity'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { collectOwnedGiftIds } from '../../lib/deckEA'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'
import type { DeckBuilderActions } from './DeckBuilderContent'
import { SECTION_STYLES } from '@/lib/constants'

export interface DeckBuilderSummaryProps extends Partial<
  Omit<DeckBuilderActions, 'onIdentityChange'>
> {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  ownedGiftIds: ReadonlySet<EGOGiftId>
  onToggleDeploy?: ((sinnerIndex: number) => void) | undefined
  onEditDeck?: (() => void) | undefined
  readOnly?: boolean
  trackerMode?: boolean
  onResetToInitial?: (() => void) | undefined
  onViewNotes?: (() => void) | undefined
}

export function DeckBuilderSummary({
  equipment,
  deploymentOrder,
  ownedGiftIds,
  onToggleDeploy,
  onImport,
  onExport,
  onResetOrder,
  onEditDeck,
  readOnly = false,
  trackerMode = false,
  onResetToInitial,
  onViewNotes,
}: DeckBuilderSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])

  const identitySpec = useIdentityListSpec()
  const identityI18n = useIdentityListI18n()
  const egoSpec = useEGOListSpec()

  const identities: IdentityEntity[] = Object.entries(identitySpec).map(([id, entry]) =>
    toIdentityEntity(id, entry, identityI18n[id] || id),
  )

  const skillDataMap: Record<string, SkillData> = (() => {
    const map: Record<string, SkillData> = {}
    Object.values(equipment).forEach((eq) => {
      const spec = identitySpec[eq.identity.id]
      if (spec) {
        map[eq.identity.id] = {
          affinities: spec.attributeType?.slice(0, 3) ?? [],
          atkTypes: spec.atkType?.slice(0, 3) ?? [],
        }
      }
    })
    return map
  })()

  const egoAffinityMap: Record<string, string> = (() => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  })()

  const deckState: DeckState = {
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }

  return (
    <PlannerSection
      title={t('pages.plannerMD.deckBuilder')}
      {...(onViewNotes !== undefined && { onViewNotes })}
    >
      <SinnerGrid
        equipment={equipment}
        deploymentOrder={deploymentOrder}
        identities={identities}
        skillDataMap={skillDataMap}
        egoAffinityMap={egoAffinityMap}
        onToggleDeploy={onToggleDeploy}
        readOnly={readOnly}
      />
      <div className="mt-5 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
        <StatusViewer deckState={deckState} ownedGiftIds={ownedGiftIds} />
        {!readOnly && (
          <div className="flex flex-col items-end gap-2">
            <DeckBuilderActionBar
              onImport={onImport}
              onExport={onExport}
              onResetOrder={onResetOrder}
              showEditDeck={true}
              onEditDeck={onEditDeck}
              trackerMode={trackerMode}
              onResetToInitial={onResetToInitial}
            />
            {trackerMode && (
              <p className={SECTION_STYLES.TEXT.captionSmall}>
                {t('pages.plannerMD.tracker.deckResetNote')}
              </p>
            )}
          </div>
        )}
      </div>
    </PlannerSection>
  )
}

export type StoreBoundDeckBuilderSummaryProps = Omit<
  DeckBuilderSummaryProps,
  'equipment' | 'deploymentOrder' | 'ownedGiftIds'
>

export function StoreBoundDeckBuilderSummary(props: StoreBoundDeckBuilderSummaryProps) {
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)
  const selectedGiftIds = usePlannerEditorStore((s) => s.selectedGiftIds)
  const observationGiftIds = usePlannerEditorStore((s) => s.observationGiftIds)
  const comprehensiveGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)
  const floorSelections = usePlannerEditorStore((s) => s.floorSelections)
  const category = usePlannerEditorStore((s) => s.category)
  const ownedGiftIds = collectOwnedGiftIds(
    { selectedGiftIds, observationGiftIds, comprehensiveGiftIds, floorSelections },
    floorCount(category),
  )

  return (
    <DeckBuilderSummary
      {...props}
      equipment={equipment}
      deploymentOrder={deploymentOrder}
      ownedGiftIds={ownedGiftIds}
    />
  )
}
