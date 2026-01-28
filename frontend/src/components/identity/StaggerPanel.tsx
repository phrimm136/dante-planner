import { useTranslation } from 'react-i18next'

interface StaggerPanelProps {
  maxHP: number
  staggerThresholds: number[]
}

/** Static array ensures Tailwind sees these classes at build time */
const STAGGER_TEXT_COLORS = ['text-stagger-1', 'text-stagger-2', 'text-stagger-3'] as const

function calculateStaggerThreshold(maxHP: number, staggerPercent: number): number {
  return Math.floor(maxHP * staggerPercent / 100)
}

export function StaggerPanel({ maxHP, staggerThresholds }: StaggerPanelProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <div className="border rounded p-3 space-y-2 h-full">
      <div className="font-semibold text-sm text-center">{t('identity.stagger')}</div>
      <div className="flex justify-evenly">
        {staggerThresholds.map((threshold, index) => {
          const percentage = threshold.toFixed(0)
          const hpValue = calculateStaggerThreshold(maxHP, threshold)

          return (
            <div key={index} className="flex flex-col items-center gap-1">
              <p className={`text-xs tabular-nums ${STAGGER_TEXT_COLORS[index]}`}>{hpValue}</p>
              <span className={`text-xs tabular-nums ${STAGGER_TEXT_COLORS[index]}`}>({percentage}%)</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
