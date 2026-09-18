import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { ExtractionInputs } from './ExtractionInputs'
import { ExtractionResults } from './ExtractionResults'
import { calculateExtraction } from '../lib/extractionCalculator'
import type { ExtractionInput, ExtractionTarget } from '../types/ExtractionTypes'

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
}

export function ExtractionCalculator() {
  const { t } = useTranslation('extraction')

  const [pulls, setPulls] = useState(DEFAULT_STATE.pulls)
  const [featuredIds, setFeaturedIds] = useState(DEFAULT_STATE.featuredIds)
  const [wantedIds, setWantedIds] = useState(DEFAULT_STATE.wantedIds)
  const [featuredEgos, setFeaturedEgos] = useState(DEFAULT_STATE.featuredEgos)
  const [wantedEgos, setWantedEgos] = useState(DEFAULT_STATE.wantedEgos)
  const [featuredAnnouncers, setFeaturedAnnouncers] = useState(DEFAULT_STATE.featuredAnnouncers)
  const [wantedAnnouncers, setWantedAnnouncers] = useState(DEFAULT_STATE.wantedAnnouncers)
  const [allEgoCollected, setAllEgoCollected] = useState(DEFAULT_STATE.allEgoCollected)
  const [currentPity, setCurrentPity] = useState(DEFAULT_STATE.currentPity)

  const handleFeaturedIdsChange = (value: number) => {
    setFeaturedIds(value)
    if (wantedIds === featuredIds || wantedIds > value) {
      setWantedIds(value)
    }
  }

  const handleFeaturedEgosChange = (value: number) => {
    setFeaturedEgos(value)
    if (wantedEgos === featuredEgos || wantedEgos > value) {
      setWantedEgos(value)
    }
  }

  const handleFeaturedAnnouncersChange = (value: number) => {
    setFeaturedAnnouncers(value)
    if (wantedAnnouncers === featuredAnnouncers || wantedAnnouncers > value) {
      setWantedAnnouncers(value)
    }
  }

  const handleAllEgoCollectedChange = (value: boolean) => {
    setAllEgoCollected(value)
  }

  const targets: ExtractionTarget[] = []

  if (wantedIds > 0) {
    targets.push({
      type: 'threeStarId',
      wantedCopies: wantedIds,
      currentCopies: 0,
    })
  }

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

  const result = calculateExtraction(input)
  const hasTargets = targets.length > 0

  return (
    <PlannerSection title={t('calculator.title')}>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
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
