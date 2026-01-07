import { useTranslation } from 'react-i18next'

interface StatusPanelProps {
  hp: number
  minSpeed: number
  maxSpeed: number
  defLevel: number
  defCorrection: number
}

export function StatusPanel({ hp, minSpeed, maxSpeed, defLevel, defCorrection }: StatusPanelProps) {
  const { t } = useTranslation()
  const defCorrectionString = defCorrection <= 0 ? String(defCorrection) : "+" + String(defCorrection)


  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">{t('identity.status')}</div>
      <div className="grid grid-cols-3">
        {/* HP */}
        <div className="flex flex-col items-center gap-1">
          <img src="/images/UI/identity/hp.webp" alt="HP" className="w-6 h-6 object-contain" />
          <span className="text-xs tabular-nums">{hp}</span>
        </div>

        {/* Speed */}
        <div className="flex flex-col items-center gap-1">
          <img
            src="/images/UI/identity/speed.webp"
            alt="Speed"
            className="w-6 h-6 object-contain"
          />
          <span className="text-xs tabular-nums">
            {minSpeed}-{maxSpeed}
          </span>
        </div>

        {/* Defense */}
        <div className="flex flex-col items-center gap-1">
          <img
            src="/images/UI/identity/defense.webp"
            alt="Defense"
            className="w-6 h-6 object-contain"
          />
          <span className="text-xs tabular-nums">
            {defLevel} ({defCorrectionString})
          </span>
        </div>
      </div>
    </div>
  )
}
