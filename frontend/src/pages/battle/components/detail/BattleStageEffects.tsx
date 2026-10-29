import { Suspense, useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import { SECTION_STYLES } from '@/lib/constants'
import { getBattleKeywordIconPath } from '@/shared/assets'
import { FormattedDescription, useBattleKeywords } from '@/shared/gameText'
import { shownStageEffects, stageEffectEntries, stageEffectGrants } from '../../lib/battleDetail'
import type { EngagementRow, StartBuff } from '../../schemas/BattleDetailSchemas'

interface BattleStageEffectsProps {
  row: EngagementRow
  startBuffs: readonly StartBuff[]
}

function StageEffectCards({ row, startBuffs }: BattleStageEffectsProps) {
  const { t } = useTranslation('database')
  const { data: keywords } = useBattleKeywords()
  const { shown, missing } = shownStageEffects(
    stageEffectEntries(startBuffs, stageEffectGrants(row)),
    keywords,
  )

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
        {shown.map(({ entry, keyword }) => (
          <div
            key={entry.keywordId}
            data-keyword-id={entry.keywordId}
            className="flex gap-3 rounded-md border border-border p-3"
          >
            <img
              src={getBattleKeywordIconPath(keyword.iconId ?? entry.keywordId)}
              alt=""
              className="h-8 w-8 shrink-0 object-contain"
            />
            <div className="min-w-0 space-y-1">
              <div className="flex flex-wrap items-baseline gap-2 text-sm font-semibold">
                <span>{keyword.name}</span>
                {entry.stack !== undefined && entry.stack > 0 && (
                  <span className="tabular-nums">{entry.stack}</span>
                )}
                {entry.side !== undefined && (
                  <span className="text-xs font-normal text-muted-foreground">
                    {t(`battle.detail.side.${entry.side}`)}
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
