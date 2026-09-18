import { useTranslation } from 'react-i18next'
import { SECTION_STYLES } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { ExtractionResult, TargetProbability } from '../types/ExtractionTypes'

interface ExtractionResultsProps {
  result: ExtractionResult
  plannedPulls: number
  currentPity: number
  hasTargets: boolean
}

function formatProbability(probability: number): string {
  const percentage = probability * 100
  return `${percentage.toFixed(1)}%`
}

function formatNumber(value: number): string {
  return value.toLocaleString()
}

function ResultRow({
  label,
  value,
  highlight = false,
  subtext,
}: {
  label: string
  value: string
  highlight?: boolean
  subtext?: string
}) {
  return (
    <div
      className={cn(
        'flex justify-between items-baseline py-2 border-b border-border last:border-b-0',
        highlight && 'bg-primary/5 -mx-2 px-2 rounded',
      )}
    >
      <div className="flex flex-col">
        <span className={SECTION_STYLES.TEXT.label}>{label}</span>
        {subtext && <span className={SECTION_STYLES.TEXT.caption}>{subtext}</span>}
      </div>
      <span className={cn('font-mono text-lg', highlight && 'font-semibold text-primary')}>
        {value}
      </span>
    </div>
  )
}

function TargetResultCard({
  result,
  t,
}: {
  result: TargetProbability
  t: ReturnType<typeof useTranslation>['t']
}) {
  const { target, probability, expectedPulls, pityApplies } = result
  const copiesNeeded = Math.max(0, target.wantedCopies - target.currentCopies)

  const typeLabels: Record<string, string> = {
    threeStarId: t('results.targetTypes.threeStarId'),
    ego: t('results.targetTypes.ego'),
    announcer: t('results.targetTypes.announcer'),
  }
  const typeLabel = typeLabels[target.type] || target.type

  if (copiesNeeded <= 0) {
    return null
  }

  return (
    <div className="bg-muted/50 rounded-md p-3 space-y-2">
      <div className="flex justify-between items-center">
        <span className={SECTION_STYLES.TEXT.label}>{typeLabel}</span>
        <span className={SECTION_STYLES.TEXT.caption}>
          {t('results.copiesWanted', { count: copiesNeeded })}
        </span>
      </div>
      <div className="flex justify-between items-center">
        <span className={SECTION_STYLES.TEXT.caption}>{t('results.probability')}</span>
        <span className="font-mono font-semibold">
          {formatProbability(probability)}
          {pityApplies && (
            <span className="text-xs text-muted-foreground ml-1">
              ({t('results.pityGuaranteed')})
            </span>
          )}
        </span>
      </div>
      <div className="flex justify-between items-center">
        <span className={SECTION_STYLES.TEXT.caption}>{t('results.expectedPulls')}</span>
        <span className="font-mono">
          {expectedPulls === Infinity ? '∞' : formatNumber(Math.ceil(expectedPulls))}
        </span>
      </div>
    </div>
  )
}

export function ExtractionResults({ result, currentPity, hasTargets }: ExtractionResultsProps) {
  const { t } = useTranslation('extraction')

  const activeTargetResults = result.targetResults.filter(
    (r) => r.target.wantedCopies - r.target.currentCopies > 0,
  )

  const successiveProbs = result.successiveProbabilities.filter(
    (sp) => sp.count < result.totalItemsWanted,
  )

  return (
    <div className={cn(SECTION_STYLES.container, 'space-y-6')}>
      <div className="space-y-2">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>{t('results.summary')}</h3>

        {!hasTargets ? (
          <p className={cn(SECTION_STYLES.TEXT.caption, 'py-4 text-center')}>
            {t('results.noTargets')}
          </p>
        ) : (
          <div className="space-y-1">
            <ResultRow
              label={t('results.allTargetsCount', { count: result.totalItemsWanted })}
              value={formatProbability(result.allTargetProbability)}
              highlight
            />

            {successiveProbs.length > 0 && (
              <details className="pt-1">
                <summary className="cursor-pointer text-sm text-muted-foreground hover:text-foreground transition-colors py-1">
                  {t('results.successiveProbs')}
                </summary>
                <div className="mt-1 space-y-0">
                  {successiveProbs.map(({ count, probability }) => (
                    <ResultRow
                      key={count}
                      label={t('results.atLeastItems', { count })}
                      value={formatProbability(probability)}
                    />
                  ))}
                </div>
              </details>
            )}
          </div>
        )}
      </div>

      {hasTargets && activeTargetResults.length > 0 && (
        <div className="space-y-3">
          <h3 className={SECTION_STYLES.TEXT.subHeader}>{t('results.breakdown')}</h3>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            {activeTargetResults.map((targetResult, index) => (
              <TargetResultCard key={index} result={targetResult} t={t} />
            ))}
          </div>
        </div>
      )}

      <div className="space-y-2">
        <h3 className={SECTION_STYLES.TEXT.subHeader}>{t('results.cost')}</h3>
        <div className="space-y-1">
          <ResultRow label={t('results.lunacyCost')} value={formatNumber(result.lunacyCost)} />
          <ResultRow
            label={t('results.pullsUntilPity')}
            value={formatNumber(result.pullsUntilPity)}
            subtext={t('results.currentPity', { current: currentPity })}
          />
        </div>
      </div>

      <div className="text-center">
        <span className={SECTION_STYLES.TEXT.caption}>
          {t('results.rateTable', {
            table: t(`rateTables.${result.activeRateTable}`),
          })}
        </span>
      </div>
    </div>
  )
}
