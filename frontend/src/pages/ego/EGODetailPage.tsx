import { useParams } from '@tanstack/react-router'
import { EGOIdSchema } from '@/shared/gameData'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { EntityDetailLayout } from '@/components/layout/EntityDetailLayout'
import { DetailSection } from '@/components/layout/DetailSection'
import { DetailEntitySelector } from '@/components/layout/DetailEntitySelector'
import { useEGODetailSpec } from '@/pages/ego'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { getEGOTierIconPath } from '@/shared/assets'
import { MIN_ENTITY_TIER } from '@/shared/gameData'
import { EGODetailSkeleton } from './components/EGODetailSkeleton'
import { EGOHeader } from './components/EGOHeader'
import { EGOHeaderWithI18n } from './components/EGOHeaderI18n'
import { EGOInfoPane } from './components/EGOInfoPane'
import { SkillsSectionI18n } from './components/EGOSkillI18n'
import { PassiveCardWithSuspense } from './components/PassiveI18n'
import { EGOPassivesPane } from '@/shared/passive'
import type { Threadspin } from '@/pages/ego'

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

  const threadspinLevel = threadspin as Threadspin

  const header = (
    <Suspense fallback={<EGOHeader egoId={egoId} name="" rank={spec.egoType} skillType="awaken" />}>
      <EGOHeaderWithI18n id={egoId} rank={spec.egoType} skillType="awaken" />
    </Suspense>
  )

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

  return (
    <EntityDetailLayout header={header} metadata={<EGOInfoPane ego={spec} />} selector={selector}>
      {visibleSections >= 1 && (
        <DetailSection title={t('section.skills')}>
          <SkillsSectionI18n egoId={id} skills={spec.skills} threadspin={threadspinLevel} />
        </DetailSection>
      )}
      {visibleSections >= 2 && (
        <DetailSection title={t('section.passives')}>
          <EGOPassivesPane
            passives={spec.passives}
            threadspinLevel={threadspinLevel}
            renderPassive={(passiveId, isLocked) => (
              <PassiveCardWithSuspense id={id} passiveId={passiveId} isLocked={isLocked} />
            )}
          />
        </DetailSection>
      )}
    </EntityDetailLayout>
  )
}

export default function EGODetailPage() {
  return (
    <Suspense fallback={<EGODetailSkeleton />}>
      <EGODetailContent />
    </Suspense>
  )
}
