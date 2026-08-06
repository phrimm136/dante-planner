import React from 'react'
import { EA_SURPLUS_THRESHOLD } from '@/shared/gameData'
import { getAffinityIconPath, getBattleKeywordIconPath } from '@/shared/assets'
import { useIdentityListData } from '@/pages/identity'
import { useEGOListData } from '@/pages/ego'
import type { DeckState } from '../../types/DeckTypes'
import { computeAffinityEA, computeKeywordEA } from '../../lib/deckEA'
import { SECTION_STYLES } from '@/lib/constants'

interface StatusViewerProps {
  deckState: DeckState
}

export const StatusViewer: React.FC<StatusViewerProps> = ({ deckState }) => {
  // Load spec data using hooks (React Query caches shared across components)
  const { spec: identitySpec } = useIdentityListData()
  const { spec: egoSpec } = useEGOListData()

  const affinityCounts = computeAffinityEA(deckState, identitySpec, egoSpec)
  const keywordCounts = computeKeywordEA(deckState, identitySpec)

  return (
    <div className="border rounded-lg p-3 space-y-2">
      {/* Affinity EA */}
      <div className={SECTION_STYLES.LAYOUT.wrap}>
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
            <span className="text-xs font-bold">
              {generated}|{consumed}
            </span>
          </div>
        ))}
      </div>
      {/* Keyword EA */}
      <div className="flex flex-wrap gap-2 min-h-7 items-center">
        {keywordCounts.map(({ keyword, count, deployedCount, allCount }) => {
          // Determine text color based on EA thresholds
          const textColorClass =
            deployedCount >= EA_SURPLUS_THRESHOLD
              ? 'text-yellow-400'
              : allCount >= EA_SURPLUS_THRESHOLD
                ? 'text-cyan-400'
                : ''

          return (
            <div
              key={keyword}
              className={`flex items-center gap-1 px-2 py-1 bg-muted rounded-md ${count === 0 ? 'opacity-40' : ''}`}
              title={keyword}
            >
              <img
                src={getBattleKeywordIconPath(keyword)}
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
}

export default StatusViewer
