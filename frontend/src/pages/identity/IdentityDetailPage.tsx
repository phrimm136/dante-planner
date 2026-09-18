import { useParams } from '@tanstack/react-router'
import { IdentityIdSchema } from '@/shared/gameData'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useIdentityDetailSpec } from '@/pages/identity'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { DetailRightPanel } from '@/components/layout/DetailRightPanel'
import { MobileDetailTabs } from '@/components/layout/MobileDetailTabs'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { MAX_LEVEL, MAX_ENTITY_TIER, MIN_ENTITY_TIER } from '@/shared/gameData'
import { IdentityDetailSkeleton } from './components/IdentityDetailSkeleton'
import { IdentityInfoPane } from './components/IdentityInfoPane'
import { IdentitySkillsPane } from './components/IdentitySkillsPane'
import { IdentityPassivesPane } from './components/IdentityPassivesPane'
import { IdentitySanityPane } from './components/IdentitySanityPane'
import type { Uptie } from '@/pages/identity'

function IdentityDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  const [uptie, setUptie] = useState<number>(MAX_ENTITY_TIER.identity)
  const [level, setLevel] = useState<number>(MAX_LEVEL)

  const totalSections = 3
  const visibleSections = useProgressiveCount({ total: totalSections, step: 1, initial: 0 })

  if (!id) {
    throw new Error('Identity ID is required')
  }
  const identityId = IdentityIdSchema.parse(id)

  const identityData = useIdentityDetailSpec(identityId)

  const uptieLevel = uptie as Uptie

  const selector = (
    <DetailEntitySelector
      tierLabel={t('tierLabel.uptie')}
      minTier={MIN_ENTITY_TIER.identity}
      maxTier={MAX_ENTITY_TIER.identity}
      tier={uptie}
      onTierChange={setUptie}
      tierIconPath={getEGOTierIconPath}
      level={level}
      onLevelChange={setLevel}
      sticky
    />
  )

  const leftColumn = (
    <IdentityInfoPane id={identityId} identity={identityData} uptie={uptieLevel} level={level} />
  )

  const skillsContent = (
    <IdentitySkillsPane id={id} skills={identityData.skills} uptieLevel={uptieLevel} />
  )

  const passivesContent = (
    <IdentityPassivesPane id={id} passives={identityData.passives} uptieLevel={uptieLevel} />
  )

  const sanityContent = (
    <IdentitySanityPane
      panicType={identityData.panicType}
      mentalConditionInfo={identityData.mentalConditionInfo}
    />
  )

  const rightColumn = (
    <DetailRightPanel selector={selector}>
      {visibleSections >= 1 && skillsContent}
      {visibleSections >= 2 && passivesContent}
      {visibleSections >= 3 && sanityContent}
    </DetailRightPanel>
  )

  const mobileTabsContent =
    visibleSections >= totalSections ? (
      <>
        <div className="mb-4">{selector}</div>
        <MobileDetailTabs
          skillsContent={skillsContent}
          passivesContent={passivesContent}
          thirdTabContent={sanityContent}
        />
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

export default function IdentityDetailPage() {
  return (
    <Suspense fallback={<IdentityDetailSkeleton />}>
      <IdentityDetailContent />
    </Suspense>
  )
}
