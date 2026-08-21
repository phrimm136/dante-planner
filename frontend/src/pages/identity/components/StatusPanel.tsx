import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getHPIconPath, getSpeedIconPath, getDefenseLevelIconPath } from '@/shared/assets'

interface StatusPanelProps {
  hp: number
  minSpeed: number
  maxSpeed: number
  defLevel: number
  defCorrection: number
}

export function StatusPanel({ hp, minSpeed, maxSpeed, defLevel, defCorrection }: StatusPanelProps) {
  const { t } = useTranslation(['database', 'common'])
  const defCorrectionString =
    defCorrection <= 0 ? String(defCorrection) : '+' + String(defCorrection)

  return (
    <LabeledPanel title={t('identity.status')}>
      <div className="grid grid-cols-3">
        {/* HP */}
        <div className="flex flex-col items-center gap-1">
          <img src={getHPIconPath()} alt="HP" className="w-6 h-6 object-contain" />
          <span className="text-xs tabular-nums">{hp}</span>
        </div>

        {/* Speed */}
        <div className="flex flex-col items-center gap-1">
          <img src={getSpeedIconPath()} alt="Speed" className="w-6 h-6 object-contain" />
          <span className="text-xs tabular-nums">
            {minSpeed}-{maxSpeed}
          </span>
        </div>

        {/* Defense */}
        <div className="flex flex-col items-center gap-1">
          <img src={getDefenseLevelIconPath()} alt="Defense" className="w-6 h-6 object-contain" />
          <span className="text-xs tabular-nums">
            {defLevel} ({defCorrectionString})
          </span>
        </div>
      </div>
    </LabeledPanel>
  )
}
