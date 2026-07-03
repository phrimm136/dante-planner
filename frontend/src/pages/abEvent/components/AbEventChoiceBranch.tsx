import { useTranslation } from 'react-i18next'

import { ColoredText } from '@/shared/gameText'
import { CantSelectCondition } from './CantSelectCondition'
import { AFFINITY_COLORS, formatAdderInfo } from '../lib/abEventTextResolver'
import type { CoinTossI18nContext } from '../lib/abEventTextResolver'
import type { AbEventChoice, AbEventSelectionEvent, AbEventI18n } from '../schemas/AbEventSchemas'
import { cn } from '@/lib/utils'

type OptionI18n = NonNullable<AbEventI18n['options']>[number]
type SelectionTextI18n = NonNullable<AbEventI18n['selectionTexts']>[string]
type ResolveEffectText = (effect: string, giftId?: number, amount?: number, target?: string, condition?: string, descId?: string) => string | null
type TFunc = (key: string, options?: Record<string, unknown>) => string
type SubEventMap = Record<string, { choices?: AbEventChoice[]; selectionEvents?: Record<string, AbEventSelectionEvent> }>
type SubEventTextMap = Record<string, { name?: string; desc?: string; options?: OptionI18n[]; selectionTexts?: Record<string, SelectionTextI18n> }>

/** Shared rendering context passed through the recursive tree */
interface RenderContext {
  processText: (text: string) => string
  resolveEffectText: ResolveEffectText
  allSelectionEvents?: Record<string, AbEventSelectionEvent>
  allSelectionTexts?: Record<string, SelectionTextI18n>
  subEvents?: SubEventMap
  subEventTexts?: SubEventTextMap
  i18nCtx: CoinTossI18nContext
}

// =============================================================================
// Condition Label Formatting
// =============================================================================

const CONDITION_KEY_MAP: Record<string, string> = {
  MpAverage_Under: 'abEvent.condMpAverageUnder',
  MpAverage_NotLessThan: 'abEvent.condMpAverageNotLessThan',
  Failed_Under: 'abEvent.condFailedUnder',
  Failed_NotLessThan: 'abEvent.condFailedNotLessThan',
}

export function formatConditionLabel(condition: string, t: TFunc): string {
  if (!condition) return ''
  const probMatch = condition.match(/^Prob_([\d.]+)$/)
  if (probMatch) return t('abEvent.condProb', { value: Math.round(parseFloat(probMatch[1]) * 100) })
  const cumMatch = condition.match(/^ProbTimesRepeatCount_([-\d.]+)$/)
  if (cumMatch) {
    const val = parseFloat(cumMatch[1])
    if (val < 0) return t('abEvent.condProbCumulativeFail')
    const pct = val * 100
    return t('abEvent.condProbCumulative', { value: Number.isInteger(pct) ? pct : pct.toFixed(1) })
  }
  for (const [prefix, key] of Object.entries(CONDITION_KEY_MAP)) {
    if (condition.startsWith(prefix)) {
      const value = condition.slice(prefix.length)
      return t(key, { value })
    }
  }
  return condition
}

// =============================================================================
// Shared Rendering Helpers
// =============================================================================

interface EffectEntry {
  effect: string
  reward?: { id: number | null; num: number; prob: number; type: string }
  target?: string
  condition?: string
  descId?: string
}

function EffectList({ effects, ctx }: { effects: EffectEntry[]; ctx: RenderContext }) {
  return (
    <div className="space-y-1">
      {effects.map((effect, i) => {
        const resolved = ctx.resolveEffectText(effect.effect, effect.reward?.id ?? undefined, effect.reward?.num, effect.target, effect.condition, effect.descId)
        if (!resolved) return null
        return (
          <div key={i} className="text-sm">
            <ColoredText text={ctx.processText(resolved)} />
          </div>
        )
      })}
    </div>
  )
}

function NothingHappened({ ctx }: { ctx: RenderContext }) {
  return (
    <div className="text-sm">
      <ColoredText text={ctx.resolveEffectText('Nothing') ?? ''} />
    </div>
  )
}

/** Render a branching result card (probability or conditional) */
function BranchCard({
  label,
  narrativeText,
  effects,
  nextEventId,
  selfEventId,
  ctx,
}: {
  label?: string
  narrativeText?: string
  effects: EffectEntry[]
  nextEventId?: number
  selfEventId?: string
  ctx: RenderContext
}) {
  const isSelfRef = nextEventId && selfEventId && String(nextEventId) === selfEventId
  return (
    <div className="border border-border rounded-sm overflow-hidden">
      {label && (
        <div className="bg-muted/30 px-2 py-1 border-b border-border">
          <span className="text-xs text-muted-foreground">{label}</span>
        </div>
      )}
      <div className="p-2 space-y-1">
        {narrativeText && (
          <div className="text-sm text-muted-foreground whitespace-pre-line mb-2">
            <ColoredText text={ctx.processText(narrativeText)} />
          </div>
        )}
        {effects.length > 0 ? (
          <EffectList effects={effects} ctx={ctx} />
        ) : isSelfRef ? null : (
          <NothingHappened ctx={ctx} />
        )}
        {nextEventId && !isSelfRef && ctx.subEvents?.[String(nextEventId)] && (
          <SubEventBlock subEventId={String(nextEventId)} ctx={ctx} />
        )}
      </div>
    </div>
  )
}

function BranchLabel(
  t: TFunc,
  prob?: { probability: number },
  cond?: { condition?: string },
): string | undefined {
  if (prob) return `${Math.round(prob.probability * 100)}%`
  if (cond?.condition) return formatConditionLabel(cond.condition, t)
  return undefined
}

// =============================================================================
// ChoiceBranch (Top-level)
// =============================================================================

export function ChoiceBranch({
  choice,
  option,
  selectionEvent,
  selectionText,
  processText,
  resolveEffectText,
  allSelectionEvents,
  allSelectionTexts,
  subEvents,
  subEventTexts,
  linkedSubEventId,
  i18nCtx,
}: {
  choice: AbEventChoice
  option?: OptionI18n
  selectionEvent?: AbEventSelectionEvent
  selectionText?: SelectionTextI18n
  processText: (text: string) => string
  resolveEffectText: ResolveEffectText
  allSelectionEvents?: Record<string, AbEventSelectionEvent>
  allSelectionTexts?: Record<string, SelectionTextI18n>
  subEvents?: SubEventMap
  subEventTexts?: SubEventTextMap
  linkedSubEventId?: string
  i18nCtx: CoinTossI18nContext
}) {
  const { t } = useTranslation('database')
  const ctx: RenderContext = { processText, resolveEffectText, allSelectionEvents, allSelectionTexts, subEvents, subEventTexts, i18nCtx }

  return (
    <div className="border border-border rounded-lg overflow-hidden">
      {/* Choice header */}
      <div className="bg-muted/50 px-4 py-3 border-b border-border">
        <div className="font-medium text-sm text-foreground">
          <ColoredText text={option?.message ?? `Choice ${choice.index}`} />
        </div>
        {option?.messageDesc && (
          <div className="text-xs text-muted-foreground mt-1">{option.messageDesc}</div>
        )}
        {choice.cantSelectInThisCase && !(option?.message ?? '').includes('<size') && (
          <div className="text-xs mt-1 text-cant-select">
            <CantSelectCondition condition={choice.cantSelectInThisCase} />
          </div>
        )}
      </div>

      <div className="p-4 space-y-3">
        <ChoiceResults choice={choice} option={option} ctx={ctx} t={t} />

        {choice.directEffects && choice.directEffects.length > 0 && (
          <EffectList effects={choice.directEffects} ctx={ctx} />
        )}

        {!choice.directEffects?.length && !choice.probabilityResults?.length && !choice.conditionalResults?.length && !selectionEvent && !linkedSubEventId && (
          <NothingHappened ctx={ctx} />
        )}

        {linkedSubEventId && subEvents?.[linkedSubEventId] && (
          <SubEventBlock subEventId={linkedSubEventId} ctx={ctx} />
        )}

        {selectionEvent?.judgement && (
          <CoinTossSection selectionEvent={selectionEvent} selectionText={selectionText} ctx={ctx} />
        )}
      </div>
    </div>
  )
}

/** Renders the result section of a choice (multi-result branching or single narrative) */
function ChoiceResults({ choice, option, ctx, t }: {
  choice: AbEventChoice
  option?: OptionI18n
  ctx: RenderContext
  t: TFunc
}) {
  if (option?.result && option.result.length > 1) {
    return (
      <>
        {option.result.map((text, i) => {
          const prob = choice.probabilityResults?.[i]
          const cond = choice.conditionalResults?.[i]
          return (
            <BranchCard
              key={i}
              label={BranchLabel(t, prob, cond)}
              narrativeText={text}
              effects={prob?.effects ?? cond?.effects ?? []}
              nextEventId={prob?.nextEventId ?? cond?.nextEventId}
              ctx={ctx}
            />
          )
        })}
      </>
    )
  }

  return (
    <>
      {option?.result?.map((text, i) => (
        <div key={i} className="text-sm text-muted-foreground whitespace-pre-line">
          <ColoredText text={ctx.processText(text)} />
        </div>
      ))}
      {choice.probabilityResults && choice.probabilityResults.length > 0 && (
        <div className="space-y-2">
          {choice.probabilityResults.map((pr, i) => (
            <BranchCard
              key={i}
              label={`${Math.round(pr.probability * 100)}%`}
              effects={pr.effects}
              ctx={ctx}
            />
          ))}
        </div>
      )}
    </>
  )
}

// =============================================================================
// SubEventBlock (Recursive)
// =============================================================================

function SubEventBlock({ subEventId, ctx }: { subEventId: string; ctx: RenderContext }) {
  const { t } = useTranslation('database')
  const subEvent = ctx.subEvents?.[subEventId]
  const subText = ctx.subEventTexts?.[subEventId]
  if (!subEvent?.choices) return null

  return (
    <div className="mt-3 border border-border rounded-md overflow-hidden">
      {subText?.desc && (
        <div className="bg-muted/30 px-3 py-2 border-b border-border">
          <div className="text-sm text-muted-foreground whitespace-pre-line">
            <ColoredText text={ctx.processText(subText.desc)} />
          </div>
        </div>
      )}
      <div className="p-3 space-y-2">
        {subEvent.choices.map((choice, idx) => {
          const opt = subText?.options?.[idx]
          return (
            <SubEventChoice
              key={choice.index}
              choice={choice}
              option={opt}
              subEventId={subEventId}
              subEvent={subEvent}
              subText={subText}
              ctx={ctx}
              t={t}
            />
          )
        })}
      </div>
    </div>
  )
}

function SubEventChoice({ choice, option, subEventId, subEvent, subText, ctx, t }: {
  choice: AbEventChoice
  option?: OptionI18n
  subEventId: string
  subEvent: NonNullable<SubEventMap[string]>
  subText?: SubEventTextMap[string]
  ctx: RenderContext
  t: TFunc
}) {
  return (
    <div className="border border-border rounded-md overflow-hidden">
      <div className="bg-muted/50 px-3 py-2 border-b border-border">
        <div className="text-sm font-medium text-foreground">
          <ColoredText text={option?.message ?? `Choice ${choice.index}`} />
        </div>
        {option?.messageDesc && (
          <div className="text-xs text-muted-foreground mt-0.5">{option.messageDesc}</div>
        )}
        {choice.cantSelectInThisCase && (
          <div className="text-xs mt-0.5 text-cant-select">
            <CantSelectCondition condition={choice.cantSelectInThisCase} />
          </div>
        )}
      </div>
      <div className="p-3 space-y-2">
        {/* Single result */}
        {option?.result && option.result.length === 1 && (
          <div className="text-sm text-muted-foreground whitespace-pre-line">
            <ColoredText text={ctx.processText(option.result[0])} />
          </div>
        )}

        {/* Multi-result branching */}
        {option?.result && option.result.length > 1 && (
          option.result.map((text, ri) => {
            const prob = choice.probabilityResults?.[ri]
            const cond = choice.conditionalResults?.[ri]
            return (
              <BranchCard
                key={ri}
                label={BranchLabel(t, prob, cond)}
                narrativeText={text}
                effects={prob?.effects ?? cond?.effects ?? []}
                nextEventId={prob?.nextEventId ?? cond?.nextEventId}
                selfEventId={subEventId}
                ctx={ctx}
              />
            )
          })
        )}

        {/* Direct effects */}
        {choice.directEffects && choice.directEffects.length > 0 && (
          <EffectList effects={choice.directEffects} ctx={ctx} />
        )}

        {/* Nothing happened */}
        {!choice.directEffects?.length && !choice.probabilityResults?.length && !choice.conditionalResults?.length && !choice.nextEventId && (
          <NothingHappened ctx={ctx} />
        )}

        {/* Coin toss or nested sub-event via nextEventId */}
        {choice.nextEventId && (() => {
          const nextId = String(choice.nextEventId)
          if (nextId === subEventId) return null
          if (ctx.subEvents?.[nextId]) {
            return <SubEventBlock subEventId={nextId} ctx={ctx} />
          }
          const selKey = String(parseInt(nextId.slice(-2), 10))
          const sel = subEvent.selectionEvents?.[selKey] ?? ctx.allSelectionEvents?.[selKey]
          const selText = subText?.selectionTexts?.[selKey] ?? ctx.allSelectionTexts?.[selKey]
          if (sel?.judgement) {
            const mergedCtx: RenderContext = {
              ...ctx,
              allSelectionEvents: { ...ctx.allSelectionEvents, ...subEvent.selectionEvents },
              allSelectionTexts: { ...ctx.allSelectionTexts, ...subText?.selectionTexts },
            }
            return <CoinTossSection selectionEvent={sel} selectionText={selText} ctx={mergedCtx} />
          }
          return null
        })()}
      </div>
    </div>
  )
}

// =============================================================================
// CoinTossSection
// =============================================================================

function CoinTossSection({ selectionEvent, selectionText, ctx }: {
  selectionEvent: AbEventSelectionEvent
  selectionText?: SelectionTextI18n
  ctx: RenderContext
}) {
  const { t } = useTranslation('database')
  const judgement = selectionEvent.judgement
  if (!judgement) return null

  const adderTexts = selectionEvent.adderInfo
    ? formatAdderInfo(
        selectionEvent.adderInfo,
        ctx.i18nCtx.unitKeywords,
        ctx.i18nCtx.sinnerNames,
        ctx.i18nCtx.identityNames,
      )
    : []

  return (
    <div className="border border-border rounded-md overflow-hidden">
      {/* Coin toss header */}
      <div className="bg-muted/30 px-3 py-2 border-b border-border">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-muted-foreground">
            {selectionText?.behaveDesc ?? t('abEvent.coinToss', 'Coin Toss')}
          </span>
          <span className="text-xs font-semibold">
            {judgement.affinities.map((aff, i) => (
              <span key={aff}>
                {i > 0 && ' / '}
                <span style={{ color: AFFINITY_COLORS[aff] ?? '#ccc' }}>
                  {ctx.i18nCtx.affinityNames[aff] ?? aff}
                </span>
              </span>
            ))}
          </span>
          <span className="text-xs font-semibold text-foreground ml-auto">
            {judgement.successThreshold}+
          </span>
        </div>
        {adderTexts.length > 0 && (
          <div className="mt-1 flex flex-wrap gap-2">
            {adderTexts.map((text, i) => (
              <span key={i} className="text-xs px-1.5 py-0.5 rounded bg-muted border border-border text-foreground">
                {text}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* SUCCESS / FAILURE outcomes */}
      {selectionEvent.results?.map((result) => (
        <CoinTossOutcome key={result.outcome} result={result} selectionText={selectionText} ctx={ctx} t={t} />
      ))}
    </div>
  )
}

function CoinTossOutcome({ result, selectionText, ctx, t }: {
  result: { outcome: string; effects: EffectEntry[]; nextEventId?: number; subResults?: Array<{ condition?: string; probability?: number; effects: EffectEntry[]; nextEventId?: number }> }
  selectionText?: SelectionTextI18n
  ctx: RenderContext
  t: TFunc
}) {
  const isSuccess = result.outcome === 'SUCCESS'
  const descArray = isSuccess ? selectionText?.successDesc : selectionText?.failureDesc
  const descs = Array.isArray(descArray) ? descArray : descArray ? [descArray] : []

  return (
    <div className={cn('px-4 py-3 border-b border-border last:border-b-0')}>
      <div className="text-xs font-semibold mb-2" style={{ color: isSuccess ? '#00ff9c' : '#e30000' }}>
        {isSuccess ? ctx.i18nCtx.successLabel : ctx.i18nCtx.failureLabel}
      </div>

      {result.subResults ? (
        <div className="mt-2 space-y-2">
          {result.subResults.map((sub, si) => {
            const label = sub.probability !== undefined
              ? `${Math.round(sub.probability * 100)}%`
              : sub.condition ? formatConditionLabel(sub.condition, t) : undefined
            return (
              <BranchCard
                key={si}
                label={label}
                narrativeText={descs[si]}
                effects={sub.effects}
                nextEventId={sub.nextEventId}
                ctx={ctx}
              />
            )
          })}
        </div>
      ) : (
        <>
          {descs.map((text, i) => (
            <div key={i} className="text-sm text-muted-foreground whitespace-pre-line">
              <ColoredText text={ctx.processText(text)} />
            </div>
          ))}
          {result.effects.length > 0 && (
            <div className="mt-2">
              <EffectList effects={result.effects} ctx={ctx} />
            </div>
          )}
        </>
      )}

      {result.nextEventId && !result.subResults && (() => {
        const nextIdStr = String(result.nextEventId)
        if (ctx.subEvents?.[nextIdStr]) {
          return (
            <div className="mt-3">
              <SubEventBlock subEventId={nextIdStr} ctx={ctx} />
            </div>
          )
        }
        const chainedKey = String(parseInt(nextIdStr.slice(-2), 10))
        const chainedSel = ctx.allSelectionEvents?.[chainedKey]
        const chainedText = ctx.allSelectionTexts?.[chainedKey]
        if (!chainedSel?.judgement) return null
        return (
          <div className="mt-3">
            <CoinTossSection selectionEvent={chainedSel} selectionText={chainedText} ctx={ctx} />
          </div>
        )
      })()}
    </div>
  )
}
