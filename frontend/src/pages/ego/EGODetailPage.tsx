import { useParams } from '@tanstack/react-router'
import { EGOIdSchema } from '@/shared/gameData'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { DetailRightPanel } from '@/components/layout/DetailRightPanel'
import { MobileDetailTabs } from '@/components/layout/MobileDetailTabs'
import { useEGODetailSpec } from '@/pages/ego'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { MIN_ENTITY_TIER } from '@/shared/gameData'
import { EGODetailSkeleton } from './components/EGODetailSkeleton'
import { EGOInfoPane } from './components/EGOInfoPane'
import { EGOSkillsPane } from './components/EGOSkillsPane'
import { EGOPassivesPane } from './components/EGOPassivesPane'
import type { EgoSkillType, Threadspin } from '@/pages/ego'

function EGODetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  const totalSections = 2
  const visibleSections = useProgressiveCount({ total: totalSections, step: 1, initial: 0 })

  if (!id) {
    throw new Error('EGO ID is required')
  }
  const egoId = EGOIdSchema.parse(id)

  const spec = useEGODetailSpec(egoId)

  const [threadspin, setThreadspin] = useState<number>(spec.maxThreadspin)

  const [skillType, setSkillType] = useState<EgoSkillType>('awaken')

  const threadspinLevel = threadspin as Threadspin

  const selector = (
    <DetailEntitySelector
      tierLabel={t('tierLabel.threadspin')}
      minTier={MIN_ENTITY_TIER.ego}
      maxTier={spec.maxThreadspin}
      tier={threadspin}
      onTierChange={setThreadspin}
      tierIconPath={getEGOTierIconPath}
      sticky
    />
  )

  const leftColumn = <EGOInfoPane id={egoId} ego={spec} skillType={skillType} />

  const skillsContent = (
    <EGOSkillsPane
      id={id}
      skills={spec.skills}
      threadspinLevel={threadspinLevel}
      skillType={skillType}
      onSkillTypeChange={setSkillType}
    />
  )

  const passivesContent = (
    <EGOPassivesPane id={id} passives={spec.passives} threadspinLevel={threadspinLevel} />
  )

  const rightColumn = (
    <DetailRightPanel selector={selector}>
      {visibleSections >= 1 && skillsContent}
      {visibleSections >= 2 && passivesContent}
    </DetailRightPanel>
  )

  const mobileTabsContent =
    visibleSections >= totalSections ? (
      <>
        <div className="mb-4">{selector}</div>
        <MobileDetailTabs skillsContent={skillsContent} passivesContent={passivesContent} />
      </>
    ) : (
      <>
        <div className="mb-4">{selector}</div>
        {visibleSections >= 1 && skillsContent}
      </>
    )

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileTabsContent}
    />
  )
}

export default function EGODetailPage() {
  return (
    <Suspense fallback={<EGODetailSkeleton />}>
      <EGODetailContent />
    </Suspense>
  )
}
