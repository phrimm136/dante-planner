import React, { useMemo, memo } from 'react'
import { AFFINITIES, SINNERS, STATUS_EFFECTS } from '@/lib/constants'
import { getAffinityIconPath, getStatusEffectIconPath } from '@/lib/assetPaths'
import { useIdentityListData } from '@/hooks/useIdentityListData'
import { useEGOListData } from '@/hooks/useEGOListData'
import type { DeckState, AffinityCount, KeywordCount } from '@/types/DeckTypes'

interface StatusViewerProps {
  deckState: DeckState
}

export const StatusViewer: React.FC<StatusViewerProps> = memo(({ deckState }) => {
  // Load spec data using hooks (React Query caches shared across components)
  const { spec: identitySpec } = useIdentityListData()
  const { spec: egoSpec } = useEGOListData()

  // Get deployed sinner names (including backups)
  const deployedSinners = useMemo(() => {
    return deckState.deploymentOrder
      .map((index) => SINNERS[index])
      .filter(Boolean)
  }, [deckState.deploymentOrder])

  // Split into deployed-only (first 7) and all sinners for keyword EA calculation
  const deployedOnlySinners = useMemo(() => {
    return deckState.deploymentOrder
      .slice(0, deckState.deploymentConfig.maxDeployed)
      .map((index) => SINNERS[index])
      .filter(Boolean)
  }, [deckState.deploymentOrder, deckState.deploymentConfig.maxDeployed])

  // Calculate affinity EA (generated from skills, consumed by EGOs) - only for deployed sinners
  const affinityCounts = useMemo<AffinityCount[]>(() => {
    const counts: Record<string, { generated: number; consumed: number }> = {}
    AFFINITIES.forEach((affinity) => {
      counts[affinity] = { generated: 0, consumed: 0 }
    })

    // Skill weights: Skill1 = 3, Skill2 = 2, Skill3 = 1
    const SKILL_WEIGHTS = [3, 2, 1]

    // Calculate generated (from identity skill attributeTypes with weights) - only deployed sinners
    deployedSinners.forEach((sinnerName) => {
      const equipment = deckState.equipment[sinnerName]
      if (!equipment) return

      const spec = identitySpec[equipment.identity.id]
      if (!spec?.attributeType) return

      // attributeType array: [skill1Affinity, skill2Affinity, skill3Affinity]
      // Apply weights: Skill1 = 3, Skill2 = 2, Skill3 = 1
      spec.attributeType.slice(0, 3).forEach((affinity: string, index: number) => {
        if (counts[affinity]) {
          counts[affinity].generated += SKILL_WEIGHTS[index] || 1
        }
      })
    })

    // Calculate consumed (from EGO requirements) - only deployed sinners
    deployedSinners.forEach((sinnerName) => {
      const equipment = deckState.equipment[sinnerName]
      if (!equipment) return

      Object.values(equipment.egos).forEach((equippedEgo) => {
        if (!equippedEgo) return

        const spec = egoSpec[equippedEgo.id]
        if (!spec?.requirements) return

        Object.entries(spec.requirements).forEach(([affinity, cost]) => {
          if (counts[affinity] && typeof cost === 'number' && cost > 0) {
            counts[affinity].consumed += cost
          }
        })
      })
    })

    return AFFINITIES.map((affinity) => ({
      affinity,
      generated: counts[affinity].generated,
      consumed: counts[affinity].consumed,
    }))
  }, [deckState, deployedSinners, identitySpec, egoSpec])

  // Calculate keyword EA from identity skillKeywordList
  // Track both deployed-only and all sinners for highlighting
  // Always show all STATUS_EFFECTS in order, even if count is 0
  const keywordCounts = useMemo<(KeywordCount & { deployedCount: number; allCount: number })[]>(() => {
    // Initialize counts for all STATUS_EFFECTS to 0
    const deployedCounts: Record<string, number> = {}
    const allCounts: Record<string, number> = {}
    STATUS_EFFECTS.forEach((effect) => {
      deployedCounts[effect] = 0
      allCounts[effect] = 0
    })

    // Count for deployed-only sinners
    deployedOnlySinners.forEach((sinnerName) => {
      const equipment = deckState.equipment[sinnerName]
      if (!equipment) return

      const spec = identitySpec[equipment.identity.id]
      if (!spec?.skillKeywordList) return

      spec.skillKeywordList.forEach((keyword: string) => {
        if (deployedCounts[keyword] !== undefined) {
          deployedCounts[keyword] += 1
        }
      })
    })

    // Count for all sinners (deployed + backup)
    deployedSinners.forEach((sinnerName) => {
      const equipment = deckState.equipment[sinnerName]
      if (!equipment) return

      const spec = identitySpec[equipment.identity.id]
      if (!spec?.skillKeywordList) return

      spec.skillKeywordList.forEach((keyword: string) => {
        if (allCounts[keyword] !== undefined) {
          allCounts[keyword] += 1
        }
      })
    })

    // Return in STATUS_EFFECTS order with both counts
    return STATUS_EFFECTS.map((keyword) => ({
      keyword,
      count: allCounts[keyword], // Display total count
      deployedCount: deployedCounts[keyword],
      allCount: allCounts[keyword],
    }))
  }, [deckState, deployedOnlySinners, deployedSinners, identitySpec])

  return (
    <div className="border rounded-lg p-3 space-y-2">
      {/* Affinity EA */}
      <div className="flex flex-wrap gap-2">
        {affinityCounts.map(({ affinity, generated, consumed }) => (
          <div
            key={affinity}
            className="flex items-center gap-1 px-2 py-1 bg-muted rounded-md"
            title={affinity}
          >
            <img
              src={getAffinityIconPath(affinity)}
              alt={affinity}
              className="w-5 h-5 object-contain"
            />
            <span className="text-xs font-bold">{generated}|{consumed}</span>
          </div>
        ))}
      </div>
      {/* Keyword EA */}
      <div className="flex flex-wrap gap-2 min-h-7 items-center">
        {keywordCounts.map(({ keyword, count, deployedCount, allCount }) => {
          // Determine text color based on EA thresholds
          const textColorClass =
            deployedCount >= 5
              ? 'text-yellow-400'
              : allCount >= 5
                ? 'text-cyan-400'
                : ''

          return (
            <div
              key={keyword}
              className={`flex items-center gap-1 px-2 py-1 bg-muted rounded-md ${count === 0 ? 'opacity-40' : ''}`}
              title={keyword}
            >
              <img
                src={getStatusEffectIconPath(keyword)}
                alt={keyword}
                className="w-5 h-5 object-contain"
              />
              <span className={`text-xs font-bold ${textColorClass}`}>x{count}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
})

export default StatusViewer
