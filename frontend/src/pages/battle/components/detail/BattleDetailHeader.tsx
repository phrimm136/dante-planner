import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import {
  getBattleSpritePath,
  getHazardPlatePath,
  getRarityIconPath,
  getSinnerIconPath,
  getStageBackgroundPath,
} from '@/shared/assets'
import { SINNERS, SinnerScopedIdSchema, getSinnerFromId } from '@/shared/gameData'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { unitName } from '../../lib/battleDetail'
import { BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../../lib/cardLayout'
import { headerSpriteStyle } from '../../lib/headerSprite'
import type { BattleSprite } from '../../schemas/BattleSpecSchemas'
import { HAZARD_CLASSES, type BattleFamily } from '../../lib/battleVocabulary'
import type { BattlePhase } from '../../schemas/BattleDetailSchemas'

interface BattleDetailHeaderProps {
  family: BattleFamily
  id: string
  unitId: string
  config: BattleI18nConfig
  phase: BattlePhase
  classType: string | undefined
  hazardGrade: string | undefined
  mapName: string | undefined
}

function hazardClass(classType: string | undefined): string | undefined {
  return (HAZARD_CLASSES as readonly (string | undefined)[]).includes(classType)
    ? classType
    : undefined
}

function sinnerOf(family: BattleFamily, phase: BattlePhase): string | undefined {
  if (family !== 'sinnerEvent') return undefined
  const scoped = SinnerScopedIdSchema.safeParse(String(phase.identitySource))
  if (scoped.success) return getSinnerFromId(scoped.data)
  return phase.characterId === undefined ? undefined : SINNERS[phase.characterId - 1]
}

interface SpriteProps {
  family: BattleFamily
  id: string
  spriteId: string
  sprite: BattleSprite | undefined
  mapName: string | undefined
}

function spriteClassName(placed: boolean, placement: boolean): string | undefined {
  if (placed) return undefined
  return placement ? 'absolute opacity-0' : 'relative h-full w-full object-contain'
}

function BattleSpriteBox({
  family,
  id,
  spriteId,
  sprite,
  mapName,
  alt,
}: SpriteProps & { alt: string }) {
  const [missingSprite, setMissingSprite] = useState<string | undefined>(undefined)
  const [missingMap, setMissingMap] = useState<string | undefined>(undefined)
  const [loaded, setLoaded] = useState<{ spriteId: string; width: number } | undefined>(undefined)
  const placement = headerSpriteStyle(
    sprite,
    loaded?.spriteId === spriteId ? loaded.width : undefined,
  )

  return (
    <div
      data-testid="battle-detail-sprite"
      className="@container relative flex w-full items-center justify-center overflow-hidden rounded-lg bg-muted"
      style={{ aspectRatio: BATTLE_DETAIL_SPRITE_ASPECT_RATIO }}
    >
      {mapName !== undefined && missingMap !== mapName && (
        <img
          data-testid="battle-detail-background"
          src={getStageBackgroundPath(mapName)}
          alt=""
          onError={() => {
            setMissingMap(mapName)
          }}
          className="absolute inset-0 h-full w-full object-cover"
        />
      )}
      {missingSprite === spriteId ? (
        <span className="relative text-sm text-muted-foreground">{id}</span>
      ) : (
        <img
          src={getBattleSpritePath(family, spriteId)}
          alt={alt}
          onLoad={(event) => {
            setLoaded({ spriteId, width: event.currentTarget.naturalWidth })
          }}
          onError={() => {
            setMissingSprite(spriteId)
          }}
          style={placement}
          className={spriteClassName(
            placement !== undefined,
            sprite !== undefined && 'ppu' in sprite,
          )}
        />
      )}
    </div>
  )
}

interface NameProps {
  config: BattleI18nConfig
  id: string
  unitId: string
}

function useEntityName({ config, id, unitId }: NameProps): string {
  const i18n = useBattleDetailI18n(config, id)
  return unitName(i18n, unitId, id)
}

function BattleName(props: NameProps) {
  const { i18n } = useTranslation()
  const name = useEntityName(props)
  return (
    <h1 className={SECTION_STYLES.TEXT.pageTitle} style={getDisplayFontForLanguage(i18n.language)}>
      {name}
    </h1>
  )
}

function NamedSprite({ config, unitId, ...sprite }: SpriteProps & NameProps) {
  const name = useEntityName({ config, id: sprite.id, unitId })
  return <BattleSpriteBox {...sprite} alt={name} />
}

export function BattleDetailHeader({
  family,
  id,
  unitId,
  config,
  phase,
  classType,
  hazardGrade,
  mapName,
}: BattleDetailHeaderProps) {
  const sinner = sinnerOf(family, phase)
  const hazard = hazardClass(classType)
  const sprite = { family, id, spriteId: phase.spriteId, sprite: phase.sprite, mapName }

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
        {hazard !== undefined && (
          <div className="flex items-center justify-end gap-1">
            <img
              data-testid="battle-hazard-plate"
              src={getHazardPlatePath(hazard)}
              alt={hazard}
              className="h-6 object-contain"
            />
            {hazardGrade !== undefined && (
              <span
                data-testid="battle-hazard-grade"
                className="text-sm font-semibold tabular-nums"
              >
                {hazardGrade}
              </span>
            )}
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
            <BattleName config={config} id={id} unitId={unitId} />
          </Suspense>
        </div>
      </div>
      <Suspense fallback={<BattleSpriteBox {...sprite} alt="" />}>
        <NamedSprite {...sprite} config={config} unitId={unitId} />
      </Suspense>
    </div>
  )
}
