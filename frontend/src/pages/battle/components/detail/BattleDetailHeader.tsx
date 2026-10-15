import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'
import {
  getBattleSpritePath,
  getRarityIconPath,
  getSinnerIconPath,
  getStageBackgroundPath,
} from '@/shared/assets'
import { SINNERS, SinnerScopedIdSchema, getSinnerFromId } from '@/shared/gameData'
import { useIdentityListI18n } from '@/pages/identity'
import { useBattleDetailI18n, type BattleI18nConfig } from '../../hooks/useBattleDetailData'
import { unitName } from '../../lib/battleDetail'
import { envyHeaderNames, envyIdentityTitle, singleLineTitle } from '../../lib/battleListing'
import { BATTLE_DETAIL_SPRITE_ASPECT_RATIO } from '../../lib/cardLayout'
import { headerSpriteStyle } from '../../lib/headerSprite'
import type { BattleSprite } from '../../schemas/BattleSpecSchemas'
import type { BattleFamily } from '../../lib/battleVocabulary'
import type { BattlePhase, RiskPlate as RiskPlateBadge } from '../../schemas/BattleDetailSchemas'
import { RiskPlate } from './RiskPlate'

interface BattleDetailHeaderProps {
  family: BattleFamily
  id: string
  unitId: string
  config: BattleI18nConfig
  phase: BattlePhase
  riskPlate: RiskPlateBadge | undefined
  mapName: string | undefined
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
  sprite: BattleSprite | undefined
  mapName: string | undefined
}

const SPRITE_EXTENSION = /\.webp$/

function BattleSpriteBox({ family, id, sprite, mapName, alt }: SpriteProps & { alt: string }) {
  const [missingSprite, setMissingSprite] = useState<string | undefined>(undefined)
  const [missingMap, setMissingMap] = useState<string | undefined>(undefined)
  const placement = headerSpriteStyle(sprite)
  const file = sprite?.file

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
      {file === undefined || missingSprite === file ? (
        <span className="relative text-sm text-muted-foreground">{id}</span>
      ) : (
        <img
          data-testid="battle-detail-sprite-image"
          src={getBattleSpritePath(family, file.replace(SPRITE_EXTENSION, ''))}
          alt={alt}
          onError={() => {
            setMissingSprite(file)
          }}
          style={placement}
          className={placement === undefined ? 'relative h-full w-full object-contain' : undefined}
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

function BattleName({
  personalityId,
  ...props
}: NameProps & { personalityId: number | undefined }) {
  const { i18n } = useTranslation()
  const name = useEntityName(props)
  const title = envyIdentityTitle(personalityId, useIdentityListI18n())
  const tag = title === undefined ? undefined : envyHeaderNames(name, singleLineTitle(title))
  const titleStyle = getDisplayFontForLanguage(i18n.language)
  if (tag === undefined) {
    return (
      <h1 className={SECTION_STYLES.TEXT.pageTitle} style={titleStyle}>
        {name}
      </h1>
    )
  }
  return (
    <div>
      <p
        data-testid="battle-abnormality-line"
        className={SECTION_STYLES.TEXT.caption}
        style={titleStyle}
      >
        {tag.abnormalityLine}
      </p>
      <h1 className={SECTION_STYLES.TEXT.pageTitle} style={titleStyle}>
        {tag.name}
      </h1>
    </div>
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
  riskPlate,
  mapName,
}: BattleDetailHeaderProps) {
  const sinner = sinnerOf(family, phase)
  const sprite = { family, id, sprite: phase.sprite, mapName }

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
        {riskPlate !== undefined && (
          <div className="flex justify-end">
            <RiskPlate plate={riskPlate} />
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
            <BattleName
              config={config}
              id={id}
              unitId={unitId}
              personalityId={phase.overridePersonalityId}
            />
          </Suspense>
        </div>
      </div>
      <Suspense fallback={<BattleSpriteBox {...sprite} alt="" />}>
        <NamedSprite {...sprite} config={config} unitId={unitId} />
      </Suspense>
    </div>
  )
}
