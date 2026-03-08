import { useTranslation } from 'react-i18next'

import {
  getResistanceInfo,
  getSlashResistIconPath,
  getPierceResistIconPath,
  getBluntResistIconPath,
} from '@/lib/assetPaths'

interface ResistancePanelProps {
  slash: number
  pierce: number
  blunt: number
}

export function ResistancePanel({ slash, pierce, blunt }: ResistancePanelProps) {
  const { t } = useTranslation(['database', 'common'])
  const slashInfo = getResistanceInfo(slash)
  const pierceInfo = getResistanceInfo(pierce)
  const bluntInfo = getResistanceInfo(blunt)

  return (
    <div className="border rounded p-3 space-y-2 h-full">
      <div className="font-semibold text-sm text-center">{t('identity.resistances')}</div>
      <div className="flex justify-around items-center">
        {/* Slash */}
        <div className="flex flex-col items-center gap-2">
          <img
            src={getSlashResistIconPath()}
            alt="Slash"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${slashInfo.color}`}>
              {t(`identity.resist.${slashInfo.categoryKey}`)}
            </span>
            <span className={`text-xs ${slashInfo.color}`}>(x{slashInfo.value})</span>
          </div>
        </div>

        {/* Pierce */}
        <div className="flex flex-col items-center gap-2">
          <img
            src={getPierceResistIconPath()}
            alt="Pierce"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${pierceInfo.color}`}>
              {t(`identity.resist.${pierceInfo.categoryKey}`)}
            </span>
            <span className={`text-xs ${pierceInfo.color}`}>(x{pierceInfo.value})</span>
          </div>
        </div>

        {/* Blunt */}
        <div className="flex flex-col items-center gap-2">
          <img
            src={getBluntResistIconPath()}
            alt="Blunt"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${bluntInfo.color}`}>
              {t(`identity.resist.${bluntInfo.categoryKey}`)}
            </span>
            <span className={`text-xs ${bluntInfo.color}`}>(x{bluntInfo.value})</span>
          </div>
        </div>
      </div>
    </div>
  )
}
