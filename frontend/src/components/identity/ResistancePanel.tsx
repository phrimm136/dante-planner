import { useTranslation } from 'react-i18next'

import { getResistanceInfo } from '@/lib/assetPaths'

interface ResistancePanelProps {
  slash: number
  pierce: number
  blunt: number
}

export function ResistancePanel({ slash, pierce, blunt }: ResistancePanelProps) {
  const { t } = useTranslation()
  const slashInfo = getResistanceInfo(slash)
  const pierceInfo = getResistanceInfo(pierce)
  const bluntInfo = getResistanceInfo(blunt)

  return (
    <div className="border rounded p-3 space-y-2">
      <div className="font-semibold text-sm text-center">{t('identity.resistances')}</div>
      <div className="flex justify-around items-center">
        {/* Slash */}
        <div className="flex flex-col items-center gap-2">
          <img
            src="/images/UI/identity/SLASH.webp"
            alt="Slash"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${slashInfo.color}`}>{slashInfo.category}</span>
            <span className={`text-xs ${slashInfo.color}`}>(x{slashInfo.value})</span>
          </div>
        </div>

        {/* Pierce */}
        <div className="flex flex-col items-center gap-2">
          <img
            src="/images/UI/identity/PENETRATE.webp"
            alt="Pierce"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${pierceInfo.color}`}>{pierceInfo.category}</span>
            <span className={`text-xs ${pierceInfo.color}`}>(x{pierceInfo.value})</span>
          </div>
        </div>

        {/* Blunt */}
        <div className="flex flex-col items-center gap-2">
          <img
            src="/images/UI/identity/HIT.webp"
            alt="Blunt"
            className="w-6 h-6 object-contain"
          />
          <div className="flex flex-col items-center">
            <span className={`text-xs ${bluntInfo.color}`}>{bluntInfo.category}</span>
            <span className={`text-xs ${bluntInfo.color}`}>(x{bluntInfo.value})</span>
          </div>
        </div>
      </div>
    </div>
  )
}
