import { useParams } from '@tanstack/react-router'
import { IdentityIdSchema } from '@/shared/gameData'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useIdentityDetailSpec } from '@/pages/identity'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import { DetailSection } from '@/components/layout/DetailSection'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { IDENTITY_LEVEL_CAP, MAX_ENTITY_TIER, MIN_ENTITY_TIER } from '@/shared/gameData'
import { IdentityDetailSkeleton } from './components/IdentityDetailSkeleton'
import { IdentityHeader } from './components/IdentityHeader'
import { IdentityHeaderWithI18n } from './components/IdentityHeaderI18n'
import { IdentityInfoPane } from './components/IdentityInfoPane'
import { SkillsSectionI18n } from './components/IdentitySkillI18n'
import { PassiveCardI18n } from './components/PassiveI18n'
import { IdentityPassivesPane } from '@/shared/passive'
import { IdentitySanityPane } from '@/shared/sanity'
import type { Uptie } from '@/pages/identity'

function IdentityDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  const [uptie, setUptie] = useState<number>(MAX_ENTITY_TIER.identity)
  const [level, setLevel] = useState<number>(IDENTITY_LEVEL_CAP)

  const totalSections = 3
  const visibleSections = useProgressiveCount({ total: totalSections, step: 1, initial: 0 })

  if (!id) {
    throw new Error('Identity ID is required')
  }
  const identityId = IdentityIdSchema.parse(id)

  const identityData = useIdentityDetailSpec(identityId)

  const uptieLevel = uptie as Uptie

  const header = (
    <Suspense
      fallback={
        <IdentityHeader
          identityId={identityId}
          name=""
          rank={identityData.rank}
          uptie={uptieLevel}
        />
      }
    >
      <IdentityHeaderWithI18n id={identityId} rank={identityData.rank} uptie={uptieLevel} />
    </Suspense>
  )

  const selector = (
    <DetailEntitySelector
      tierLabel={t('tierLabel.uptie')}
      minTier={MIN_ENTITY_TIER.identity}
      maxTier={MAX_ENTITY_TIER.identity}
      tier={uptie}
      onTierChange={setUptie}
      tierIconPath={getEGOTierIconPath}
      level={level}
      maxLevel={IDENTITY_LEVEL_CAP}
      onLevelChange={setLevel}
      sticky
    />
  )

  return (
    <EntityDetailLayout
      header={header}
      metadata={
        <IdentityInfoPane
          id={identityId}
          identity={identityData}
          uptie={uptieLevel}
          level={level}
        />
      }
      selector={selector}
    >
      {visibleSections >= 1 && (
        <DetailSection title={t('section.skills')}>
          <SkillsSectionI18n id={id} skills={identityData.skills} uptieLevel={uptieLevel} level={level} />
        </DetailSection>
      )}
      {visibleSections >= 2 && (
        <DetailSection title={t('section.passives')}>
          <IdentityPassivesPane
            passives={identityData.passives}
            uptieLevel={uptieLevel}
            renderPassive={(passiveId, isLocked) => (
              <PassiveCardI18n
                id={id}
                passiveId={passiveId}
                condition={identityData.passives.conditions[passiveId]}
                isLocked={isLocked}
              />
            )}
          />
        </DetailSection>
      )}
      {visibleSections >= 3 && (
        <DetailSection title={t('section.sanity')}>
          <IdentitySanityPane
            panicType={identityData.panicType}
            mentalConditionInfo={identityData.mentalConditionInfo}
          />
        </DetailSection>
      )}
    </EntityDetailLayout>
  )
}

export default function IdentityDetailPage() {
  return (
    <Suspense fallback={<IdentityDetailSkeleton />}>
      <IdentityDetailContent />
    </Suspense>
  )
}
