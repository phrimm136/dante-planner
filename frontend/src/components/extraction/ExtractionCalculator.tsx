/**
 * Extraction Calculator Container
 *
 * Main container that:
 * - Manages state with useState (pulls, targets, modifiers)
 * - Calls calculateExtraction() from extractionCalculator.ts
 * - Passes state down to ExtractionInputs
 * - Passes results to ExtractionResults
 *
 * State updates are immediate (no submit button) - React Compiler handles optimization.
 *
 * @see ExtractionInputs.tsx for input controls
 * @see ExtractionResults.tsx for results display
 * @see extractionCalculator.ts for calculation logic
 */

import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/common/PlannerSection'
import { ExtractionInputs } from './ExtractionInputs'
import { ExtractionResults } from './ExtractionResults'
import { calculateExtraction } from '@/lib/extractionCalculator'
import type { ExtractionInput, ExtractionTarget } from '@/types/ExtractionTypes'

/** Default values for initial state - start empty */
const DEFAULT_STATE = {
  pulls: 0,
  featuredIds: 0,
  wantedIds: 0,
  featuredEgos: 0,
  wantedEgos: 0,
  featuredAnnouncers: 0,
  wantedAnnouncers: 0,
  allEgoCollected: false,
  currentPity: 0,
} as const

export function ExtractionCalculator() {
  const { t } = useTranslation('extraction')

  // Input state
  const [pulls, setPulls] = useState(DEFAULT_STATE.pulls)
  const [featuredIds, setFeaturedIds] = useState(DEFAULT_STATE.featuredIds)
  const [wantedIds, setWantedIds] = useState(DEFAULT_STATE.wantedIds)
  const [featuredEgos, setFeaturedEgos] = useState(DEFAULT_STATE.featuredEgos)
  const [wantedEgos, setWantedEgos] = useState(DEFAULT_STATE.wantedEgos)
  const [featuredAnnouncers, setFeaturedAnnouncers] = useState(DEFAULT_STATE.featuredAnnouncers)
  const [wantedAnnouncers, setWantedAnnouncers] = useState(DEFAULT_STATE.wantedAnnouncers)
  const [allEgoCollected, setAllEgoCollected] = useState(DEFAULT_STATE.allEgoCollected)
  const [currentPity, setCurrentPity] = useState(DEFAULT_STATE.currentPity)

  // Handle featured count changes - sync wanted to match featured (user typically wants all)
  const handleFeaturedIdsChange = (value: number) => {
    setFeaturedIds(value)
    // Sync: if wanted was at max, keep it at new max; otherwise clamp
    if (wantedIds === featuredIds || wantedIds > value) {
      setWantedIds(value)
    }
  }

  const handleFeaturedEgosChange = (value: number) => {
    setFeaturedEgos(value)
    // Sync: if wanted was at max, keep it at new max; otherwise clamp
    if (wantedEgos === featuredEgos || wantedEgos > value) {
      setWantedEgos(value)
    }
  }

  const handleFeaturedAnnouncersChange = (value: number) => {
    setFeaturedAnnouncers(value)
    // Sync: if wanted was at max, keep it at new max; otherwise clamp
    if (wantedAnnouncers === featuredAnnouncers || wantedAnnouncers > value) {
      setWantedAnnouncers(value)
    }
  }

  // Handle allEgoCollected toggle - does NOT reset wantedEgos
  // User can still want rate-up EGO even when all non-rate-up EGO are collected
  const handleAllEgoCollectedChange = (value: boolean) => {
    setAllEgoCollected(value)
  }

  // Build targets array from inputs
  // Note: allEgoCollected affects RATE, not whether EGO can be targeted
  const targets: ExtractionTarget[] = []

  if (wantedIds > 0) {
    targets.push({
      type: 'threeStarId',
      wantedCopies: wantedIds,
      currentCopies: 0,
    })
  }

  // EGO can be wanted regardless of allEgoCollected (rate-up EGO is still pullable)
  if (wantedEgos > 0 && featuredEgos > 0) {
    targets.push({
      type: 'ego',
      wantedCopies: wantedEgos,
      currentCopies: 0,
    })
  }

  if (wantedAnnouncers > 0 && featuredAnnouncers > 0) {
    targets.push({
      type: 'announcer',
      wantedCopies: wantedAnnouncers,
      currentCopies: 0,
    })
  }

  // Build input for calculator
  // featuredEgoCount is the actual count - rate adjustment happens via allEgoCollected flag
  const input: ExtractionInput = {
    plannedPulls: pulls,
    featuredThreeStarCount: featuredIds,
    featuredEgoCount: featuredEgos, // Always actual count, rate adjusted in calculator
    featuredAnnouncerCount: featuredAnnouncers,
    modifiers: {
      allEgoCollected,
      hasAnnouncer: featuredAnnouncers > 0,
    },
    targets,
    currentPity,
  }

  // Calculate results - React Compiler optimizes this
  const result = calculateExtraction(input)
  const hasTargets = targets.length > 0

  return (
    <PlannerSection title={t('calculator.title')}>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left: Inputs */}
        <ExtractionInputs
          pulls={pulls}
          featuredIds={featuredIds}
          wantedIds={wantedIds}
          featuredEgos={featuredEgos}
          wantedEgos={wantedEgos}
          featuredAnnouncers={featuredAnnouncers}
          wantedAnnouncers={wantedAnnouncers}
          allEgoCollected={allEgoCollected}
          currentPity={currentPity}
          onPullsChange={setPulls}
          onFeaturedIdsChange={handleFeaturedIdsChange}
          onWantedIdsChange={setWantedIds}
          onFeaturedEgosChange={handleFeaturedEgosChange}
          onWantedEgosChange={setWantedEgos}
          onFeaturedAnnouncersChange={handleFeaturedAnnouncersChange}
          onWantedAnnouncersChange={setWantedAnnouncers}
          onAllEgoCollectedChange={handleAllEgoCollectedChange}
          onCurrentPityChange={setCurrentPity}
        />

        {/* Right: Results */}
        <ExtractionResults
          result={result}
          plannedPulls={pulls}
          currentPity={currentPity}
          hasTargets={hasTargets}
        />
      </div>
    </PlannerSection>
  )
}
