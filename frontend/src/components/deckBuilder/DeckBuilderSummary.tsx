import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { DEFAULT_DEPLOYMENT_MAX, SECTION_STYLES } from '@/lib/constants'
import { PlannerSection } from '@/components/common/PlannerSection'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { useEGOListData } from '@/hooks/useEGOListData'
import type { SinnerEquipment, DeckState } from '@/types/DeckTypes'
import type { Identity } from '@/types/IdentityTypes'
import { SinnerGrid, type SkillData } from './SinnerGrid'
import { StatusViewer } from './StatusViewer'
import { DeckBuilderActionBar } from './DeckBuilderActionBar'

interface DeckBuilderSummaryProps {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  onToggleDeploy: (sinnerIndex: number) => void
  onImport: () => void
  onExport: () => void
  onResetOrder: () => void
  onEditDeck: () => void
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
}: DeckBuilderSummaryProps) {
  const { t } = useTranslation()

  // Load identity and EGO data (shared cache with Pane)
  const { spec: identitySpec, i18n: identityI18n } = useIdentityListData()
  const { spec: egoSpec } = useEGOListData()

  // Merge spec and i18n into Identity array for display
  const identities = useMemo<Identity[]>(() =>
    Object.entries(identitySpec).map(([id, specData]) => ({
      id,
      name: identityI18n[id] || id,
      rank: specData.rank,
      unitKeywordList: specData.unitKeywordList,
      skillKeywordList: specData.skillKeywordList,
    })),
    [identitySpec, identityI18n]
  )

  // Get skill data (affinities and attack types) for each equipped identity
  const skillDataMap = useMemo((): Record<string, SkillData> => {
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
  }, [equipment, identitySpec])

  // Get EGO affinity data (first affinity for background color)
  const egoAffinityMap = useMemo((): Record<string, string> => {
    const map: Record<string, string> = {}
    Object.entries(egoSpec).forEach(([id, spec]) => {
      if (spec.attributeType?.[0]) {
        map[id] = spec.attributeType[0]
      }
    })
    return map
  }, [egoSpec])

  // Construct deckState for StatusViewer
  const deckState: DeckState = useMemo(() => ({
    equipment,
    deploymentOrder,
    deploymentConfig: {
      maxDeployed: DEFAULT_DEPLOYMENT_MAX,
    },
  }), [equipment, deploymentOrder])

  return (
    <PlannerSection title={t('pages.plannerMD.deckBuilder')}>
      <div className="space-y-6">
        {/* Sinner Grid */}
        <div className={SECTION_STYLES.container}>
          <h3 className={`${SECTION_STYLES.TEXT.subHeader} mb-3`}>Formation</h3>
          <SinnerGrid
            equipment={equipment}
            deploymentOrder={deploymentOrder}
            identities={identities}
            skillDataMap={skillDataMap}
            egoAffinityMap={egoAffinityMap}
            onToggleDeploy={onToggleDeploy}
          />
          {/* Status + Action Bar row */}
          <div className="mt-3 flex flex-col lg:flex-row lg:items-start lg:justify-between gap-3">
            <StatusViewer deckState={deckState} />
            <DeckBuilderActionBar
              onImport={onImport}
              onExport={onExport}
              onResetOrder={onResetOrder}
              showEditDeck
              onEditDeck={onEditDeck}
            />
          </div>
        </div>
      </div>
    </PlannerSection>
  )
}
