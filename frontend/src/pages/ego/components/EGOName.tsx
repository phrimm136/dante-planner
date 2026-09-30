import { useTranslation } from 'react-i18next'

import {
  blockGeometryCentreEm,
  createAdvanceMeasure,
  faceTracking,
  fitText,
  linePitchesEm,
  nameShadow,
  pctStyle,
  useFontAdvances,
} from '@/shared/cardLayout'
import { GAME_TEXT_CLASS } from '@/lib/constants'
import { cn, getGeometryFontForLanguage } from '@/lib/utils'
import type { EGOId } from '@/shared/gameData'
import { useEGOListI18n } from '../hooks/useEGOListData'
import { EGO_NAME_CQW, EGO_NAME_RECT, EGO_NAME_TRACKING, egoNameFitSpec } from '../lib/cardLayout'

interface EGONameProps {
  id: EGOId
}

export function EGOName({ id }: EGONameProps) {
  const { i18n } = useTranslation()
  const i18nData = useEGOListI18n()
  const name = i18nData[id] ?? id

  const fontTable = useFontAdvances(i18n.language)
  const measure = createAdvanceMeasure(fontTable, EGO_NAME_TRACKING)
  const fitted = fitText(name, egoNameFitSpec(fontTable), measure)
  const tracking = faceTracking(fontTable, EGO_NAME_TRACKING)
  const pitches = linePitchesEm(fontTable, fitted.lines, EGO_NAME_CQW.lineSpacing)
  const centreEm = blockGeometryCentreEm(fontTable, fitted.lines, EGO_NAME_CQW.lineSpacing)

  return (
    <div
      data-testid="ego-name"
      style={pctStyle(EGO_NAME_RECT)}
      className={cn(
        'flex flex-col items-center justify-center pointer-events-none',
        GAME_TEXT_CLASS,
      )}
    >
      {fitted.lines.map((line, index) => (
        <span
          key={`${String(index)}:${line}`}
          data-testid="ego-name-line"
          className="text-center"
          style={{
            ...getGeometryFontForLanguage(i18n.language),
            fontSize: `${String(fitted.fontSize)}cqw`,
            lineHeight: 0,
            marginTop: `${String(pitches[index] ?? 0)}em`,
            position: 'relative',
            top: `${String(centreEm)}em`,
            letterSpacing: `${String(tracking.letterSpacingEm)}em`,
            wordSpacing: `${String(tracking.wordSpacingEm)}em`,
            whiteSpace: 'nowrap',
            textShadow: nameShadow('ego', i18n.language),
          }}
        >
          {line}
        </span>
      ))}
    </div>
  )
}
