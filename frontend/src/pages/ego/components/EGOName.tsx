import { useTranslation } from 'react-i18next'

import {
  createAdvanceMeasure,
  fitText,
  lineMetrics,
  midlineOffsetEm,
  nameShadow,
  pctStyle,
  useFontAdvances,
} from '@/shared/cardLayout'
import { getDisplayFontForLanguage } from '@/lib/utils'
import type { EGOId } from '@/shared/gameData'
import { useEGOListI18n } from '../hooks/useEGOListData'
import {
  EGO_NAME_CQW,
  EGO_NAME_RECT,
  EGO_NAME_TRACKING,
  egoNameFitSpec,
  egoNameLineHeight,
} from '../lib/cardLayout'

interface EGONameProps {
  /** EGO ID to look up name */
  id: EGOId
}

/**
 * The EGO name plate's text, broken into lines and sized to fit its box.
 *
 * Reads i18n and the display face's advance table through `useSuspenseQuery` — render it
 * inside a Suspense boundary.
 */
export function EGOName({ id }: EGONameProps) {
  const { i18n } = useTranslation()
  const i18nData = useEGOListI18n()
  const name = i18nData[id] ?? id

  const fontTable = useFontAdvances(i18n.language)
  const measure = createAdvanceMeasure(fontTable, EGO_NAME_TRACKING)
  const fitted = fitText(name, egoNameFitSpec(fontTable), measure)

  return (
    <div
      data-testid="ego-name"
      style={{
        ...pctStyle(EGO_NAME_RECT),
        transform: `translateY(${String(midlineOffsetEm(fontTable) * fitted.fontSize)}cqw)`,
      }}
      className="flex flex-col items-center justify-center pointer-events-none"
    >
      {fitted.lines.map((line, index) => (
        <span
          key={`${String(index)}:${line}`}
          data-testid="ego-name-line"
          className="text-center"
          style={{
            ...getDisplayFontForLanguage(i18n.language),
            fontSize: `${String(fitted.fontSize)}cqw`,
            lineHeight: egoNameLineHeight(lineMetrics(fontTable, line)),
            letterSpacing: `${String(EGO_NAME_CQW.letterSpacingEm)}em`,
            wordSpacing: `${String(EGO_NAME_CQW.wordSpacingEm)}em`,
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
