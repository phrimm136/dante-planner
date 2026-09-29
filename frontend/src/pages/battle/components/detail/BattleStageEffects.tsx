import { Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { getBattleKeywordIconPath } from '@/shared/assets'
import { FormattedDescription, useBattleKeywords } from '@/shared/gameText'
import { useBattleDetailI18n } from '../../hooks/useBattleDetailData'
import { stageEffectGrants, unitTextKeywords, type OwnSources } from '../../lib/battleDetail'
import type { EngagementRow } from '../../schemas/BattleDetailSchemas'
import type { BattleTextSource } from './BattleSkillCard'

interface BattleStageEffectsProps {
  source: BattleTextSource
  row: EngagementRow
  own: OwnSources
  phaseIds: readonly string[]
}

function StageEffectCards({ source, row, own, phaseIds }: BattleStageEffectsProps) {
  const { t } = useTranslation('database')
  const i18n = useBattleDetailI18n(source.config, source.id)
  const { data: keywords } = useBattleKeywords()
  const grants = stageEffectGrants(row, own, unitTextKeywords(i18n, source.unitId, phaseIds))
  const shown = grants.flatMap((grant) => {
    const keyword = keywords[grant.keywordId]
    return keyword === undefined ? [] : [{ grant, keyword }]
  })
  const missing = grants.length - shown.length

  useEffect(() => {
    if (import.meta.env.DEV && missing > 0) {
      console.warn(`[battle] ${String(missing)} stage-effect keywords have no text on ${row.stage}`)
    }
  }, [missing, row.stage])

  if (shown.length === 0) return null

  return (
    <div data-testid="battle-stage-effects" className="mt-3 space-y-2">
      <div className={SECTION_STYLES.TEXT.sectionTitle}>{t('battle.detail.stageEffects')}</div>
      <div className="grid gap-2 md:grid-cols-2">
        {shown.map(({ grant, keyword }) => (
          <div
            key={grant.keywordId}
            data-keyword-id={grant.keywordId}
            className="flex gap-3 rounded-md border border-border p-3"
          >
            <img
              src={getBattleKeywordIconPath(keyword.iconId ?? grant.keywordId)}
              alt=""
              className="h-8 w-8 shrink-0 object-contain"
            />
            <div className="min-w-0 space-y-1">
              <div className="flex flex-wrap items-baseline gap-2 text-sm font-semibold">
                <span>{keyword.name}</span>
                {grant.stack !== undefined && grant.stack > 0 && (
                  <span className="tabular-nums">{grant.stack}</span>
                )}
                {grant.target !== 'unknown' && (
                  <span className="text-xs font-normal text-muted-foreground">
                    {t(`battle.detail.side.${grant.target}`)}
                  </span>
                )}
              </div>
              <div className="text-xs">
                <FormattedDescription text={keyword.desc} />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function BattleStageEffects(props: BattleStageEffectsProps) {
  return (
    <Suspense fallback={null}>
      <StageEffectCards {...props} />
    </Suspense>
  )
}
