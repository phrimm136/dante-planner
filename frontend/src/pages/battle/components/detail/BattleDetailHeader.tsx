import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { getBattleSpritePath, getRarityIconPath, getSinnerIconPath } from '@/shared/assets'
import { SINNERS, SinnerScopedIdSchema, getSinnerFromId } from '@/shared/gameData'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../../lib/cardLayout'
import type { BattleFamily } from '../../lib/battleVocabulary'
import type { BattlePhase, EnemyData } from '../../schemas/BattleDetailSchemas'

type HazardBadge = NonNullable<EnemyData['badge']>

interface BattleDetailHeaderProps {
  family: BattleFamily
  id: string
  config: BattleI18nConfig
  phase: BattlePhase
  badge: HazardBadge | undefined
}

function sinnerOf(family: BattleFamily, phase: BattlePhase): string | undefined {
  if (family !== 'sinnerEvent') return undefined
  const scoped = SinnerScopedIdSchema.safeParse(String(phase.identitySource))
  if (scoped.success) return getSinnerFromId(scoped.data)
  return phase.characterId === undefined ? undefined : SINNERS[phase.characterId - 1]
}

function BattleSprite({
  family,
  id,
  spriteId,
  alt,
}: {
  family: BattleFamily
  id: string
  spriteId: string
  alt: string
}) {
  const [missingSprite, setMissingSprite] = useState<string | undefined>(undefined)

  return (
    <div
      data-testid="battle-detail-sprite"
      className="relative flex w-full items-center justify-center overflow-hidden rounded-lg bg-muted"
      style={{ aspectRatio: BATTLE_DETAIL_SPRITE_ASPECT_RATIO }}
    >
      {missingSprite === spriteId ? (
        <span className="text-sm text-muted-foreground">{id}</span>
      ) : (
        <img
          src={getBattleSpritePath(family, spriteId)}
          alt={alt}
          onError={() => {
            setMissingSprite(spriteId)
          }}
          className="h-full w-full object-contain"
        />
      )}
    </div>
  )
}

function useEntityName(config: BattleI18nConfig, id: string): string {
  const i18n = useBattleDetailI18n(config, id)
  return i18n.name ?? id
}

function BattleName({ config, id }: { config: BattleI18nConfig; id: string }) {
  const { i18n } = useTranslation()
  const name = useEntityName(config, id)
  return (
    <h1 className={SECTION_STYLES.TEXT.pageTitle} style={getDisplayFontForLanguage(i18n.language)}>
      {name}
    </h1>
  )
}

function NamedSprite({
  family,
  id,
  spriteId,
  config,
}: {
  family: BattleFamily
  id: string
  spriteId: string
  config: BattleI18nConfig
}) {
  const name = useEntityName(config, id)
  return <BattleSprite family={family} id={id} spriteId={spriteId} alt={name} />
}

export function BattleDetailHeader({ family, id, config, phase, badge }: BattleDetailHeaderProps) {
  const sinner = sinnerOf(family, phase)

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        {phase.rank !== undefined && (
          <div className="flex justify-end">
            <img
              src={getRarityIconPath(phase.rank)}
              alt={`${String(phase.rank)} rank`}
              className="h-6 object-contain"
            />
          </div>
        )}
        <div className="flex items-center gap-3">
          {sinner !== undefined && (
            <img
              src={getSinnerIconPath(sinner)}
              alt={sinner}
              className="h-12 w-12 object-contain"
            />
          )}
          <Suspense fallback={<TextSkeleton size="2xl" width="lg" />}>
            <BattleName config={config} id={id} />
          </Suspense>
        </div>
        {badge !== undefined && (
          <div data-testid="battle-hazard-badge" className="flex gap-1 text-xs">
            <span className="rounded border border-border px-2 py-0.5">{badge.classType}</span>
            {badge.hazardGrade !== undefined && (
              <span className="rounded border border-border px-2 py-0.5">{badge.hazardGrade}</span>
            )}
          </div>
        )}
      </div>
      <Suspense
        fallback={<BattleSprite family={family} id={id} spriteId={phase.spriteId} alt="" />}
      >
        <NamedSprite family={family} id={id} spriteId={phase.spriteId} config={config} />
      </Suspense>
    </div>
  )
}
