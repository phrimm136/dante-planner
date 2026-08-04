import { useTranslation } from 'react-i18next'
import { DEFAULT_DEPLOYMENT_MAX } from '@/shared/gameData'
import { PlannerSection } from '../PlannerSection'
import { useIdentityListData } from '@/pages/identity'
import { useEGOListData } from '@/pages/ego'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import type { SinnerEquipment, DeckState } from '../../types/DeckTypes'
import type { IdentityListItem } from '@/pages/identity'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'
import { SECTION_STYLES } from '@/lib/constants'

/** Everything the summary renders that it does not fetch for itself. */
export interface DeckBuilderSummaryProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  onToggleDeploy?: (sinnerIndex: number) => void
  onImport?: () => void
  onExport?: () => void
  onResetOrder?: () => void
  onEditDeck?: () => void
  readOnly?: boolean
  trackerMode?: boolean
  onResetToInitial?: () => void
  onViewNotes?: () => void
}

/**
 * Summary view of deck builder for main planner page.
 * Shows SinnerGrid, StatusViewer, and action buttons.
 * Clicking "Edit Deck" opens the DeckBuilderPane.
 */
export function DeckBuilderSummary({
  equipment,
  deploymentOrder,
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

  // Load identity and EGO data (shared cache with Pane)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec } = useEGOListData()

  // Merge spec and i18n into IdentityListItem array for display
  const identities: IdentityListItem[] = Object.entries(identitySpec).map(([id, specData]) => ({
    id,
    name: identityI18n[id] || id,
    rank: specData.rank,
    updateDate: specData.updateDate,
    unitKeywordList: specData.unitKeywordList,
    skillKeywordList: specData.skillKeywordList,
    battleKeywordList: specData.battleKeywordList ?? [],
    attributeTypes: specData.attributeType,
    atkTypes: specData.atkType,
    defenseTypes: specData.defenseType,
    season: specData.season,
  }))

  // Get skill data (affinities and attack types) for each equipped identity
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

  // Get EGO affinity data (first affinity for background color)
  const egoAffinityMap: Record<string, string> = (() => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  })()

  // Construct deckState for StatusViewer
  const deckState: DeckState = {
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }

  return (
    <PlannerSection title={t('pages.plannerMD.deckBuilder')} onViewNotes={onViewNotes}>
      {/* Sinner Grid */}
      <SinnerGrid
        equipment={equipment}
        deploymentOrder={deploymentOrder}
        identities={identities}
        skillDataMap={skillDataMap}
        egoAffinityMap={egoAffinityMap}
        onToggleDeploy={onToggleDeploy}
        readOnly={readOnly}
      />
      {/* Status + Action Bar row */}
      <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
        <StatusViewer deckState={deckState} />
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

/** Props a store-bound caller supplies; the deck itself comes from the store. */
export type StoreBoundDeckBuilderSummaryProps = Omit<
  DeckBuilderSummaryProps,
  'equipment' | 'deploymentOrder'
>

/** Renders the summary against the deck held by the planner editor store. */
export function StoreBoundDeckBuilderSummary(props: StoreBoundDeckBuilderSummaryProps) {
  const equipment = usePlannerEditorStore((s) => s.equipment)
  const deploymentOrder = usePlannerEditorStore((s) => s.deploymentOrder)

  return <DeckBuilderSummary {...props} equipment={equipment} deploymentOrder={deploymentOrder} />
}
