import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { getHPIconPath, getSpeedIconPath, getDefenseLevelIconPath } from '@/shared/assets'
import { RESIST_LEVEL_COLOURS } from '@/shared/gameData'

export type Shown<T> = { kind: 'shown'; value: T } | { kind: 'blank' }

interface StatusPanelProps {
  hp: number
  speed: Shown<{ min: number; max: number }>
  defense: Shown<{ level: number; correction: number }>
}

function signed(correction: number): string {
  return correction <= 0 ? String(correction) : '+' + String(correction)
}

export function BlankValue() {
  return (
    <span className="text-xs" style={{ color: RESIST_LEVEL_COLOURS.blank }}>
      -
    </span>
  )
}

export function StatusPanel({ hp, speed, defense }: StatusPanelProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <LabeledPanel title={t('identity.status')}>
      <div className="grid grid-cols-3">
        <div className="flex flex-col items-center gap-1">
          <img src={getHPIconPath()} alt="HP" className="w-6 h-6 object-contain" />
          <span className="text-xs tabular-nums">{hp}</span>
        </div>

        <div className="flex flex-col items-center gap-1">
          <img src={getSpeedIconPath()} alt="Speed" className="w-6 h-6 object-contain" />
          {speed.kind === 'shown' ? (
            <span className="text-xs tabular-nums">
              {speed.value.min}-{speed.value.max}
            </span>
          ) : (
            <BlankValue />
          )}
        </div>

        <div className="flex flex-col items-center gap-1">
          <img src={getDefenseLevelIconPath()} alt="Defense" className="w-6 h-6 object-contain" />
          {defense.kind === 'shown' ? (
            <span className="text-xs tabular-nums">
              {defense.value.level} ({signed(defense.value.correction)})
            </span>
          ) : (
            <BlankValue />
          )}
        </div>
      </div>
    </LabeledPanel>
  )
}
