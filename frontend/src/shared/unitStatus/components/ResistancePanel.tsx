import { useTranslation } from 'react-i18next'

import { LabeledPanel } from '@/components/layout/LabeledPanel'
import {
  getSlashResistIconPath,
  getPierceResistIconPath,
  getBluntResistIconPath,
} from '@/shared/assets'
import { RESIST_LEVEL_COLOURS, resistLevel } from '@/shared/gameData'

import { BlankValue, type Shown } from './StatusPanel'

interface ResistancePanelProps {
  resist: Shown<{ slash: number; pierce: number; blunt: number }>
}

export function ResistReading({ value }: { value: number | undefined }) {
  const { t } = useTranslation('database')
  if (value === undefined) return <BlankValue />
  const level = resistLevel(value)
  const color = RESIST_LEVEL_COLOURS[level]
  return (
    <div className="flex flex-col items-center">
      <span className="text-xs" style={{ color }}>
        {t(`identity.resist.${level}`)}
      </span>
      <span className="text-xs" style={{ color }}>
        (x{value})
      </span>
    </div>
  )
}

export function ResistancePanel({ resist }: ResistancePanelProps) {
  const { t } = useTranslation(['database', 'common'])
  const shown = resist.kind === 'shown' ? resist.value : undefined

  return (
    <LabeledPanel title={t('identity.resistances')}>
      <div className="flex justify-around items-center">
        <div className="flex flex-col items-center gap-2">
          <img src={getSlashResistIconPath()} alt="Slash" className="w-6 h-6 object-contain" />
          <ResistReading value={shown?.slash} />
        </div>

        <div className="flex flex-col items-center gap-2">
          <img src={getPierceResistIconPath()} alt="Pierce" className="w-6 h-6 object-contain" />
          <ResistReading value={shown?.pierce} />
        </div>

        <div className="flex flex-col items-center gap-2">
          <img src={getBluntResistIconPath()} alt="Blunt" className="w-6 h-6 object-contain" />
          <ResistReading value={shown?.blunt} />
        </div>
      </div>
    </LabeledPanel>
  )
}
