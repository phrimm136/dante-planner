import { useTranslation } from 'react-i18next'
import {
  createAdvanceMeasure,
  faceTracking,
  fitFontSize,
  geometryCentreEm,
  nameShadow,
  useFontAdvances,
  type PctRect,
} from '@/shared/cardLayout'
import { GAME_TEXT_CLASS } from '@/lib/constants'
import { cn, getGeometryFontForLanguage } from '@/lib/utils'
import { applyTextStyles, tmpPlainText } from '@/shared/gameText'
import { useThemePackListI18n } from '@/shared/themePack'
import {
  THEME_PACK_NAME_MAX_CQW,
  THEME_PACK_NAME_TRACKING,
  themePackCardName,
} from '../lib/cardLayout'
import type { ThemePackSpec } from '../types/ThemePackTypes'

interface ThemePackNameProps {
  packId: string
  packEntry: ThemePackSpec
  rect: PctRect
}

export function ThemePackName({ packId, packEntry, rect }: ThemePackNameProps) {
  const { i18n } = useTranslation()
  const names = useThemePackListI18n()
  const fontTable = useFontAdvances(i18n.language)

  const entry = names[packId]
  const specialName = entry?.specialName
  const text = themePackCardName(specialName ? tmpPlainText(specialName) : (entry?.name ?? packId))

  const measure = createAdvanceMeasure(fontTable, THEME_PACK_NAME_TRACKING)
  const fontSize = fitFontSize(
    text,
    { max: THEME_PACK_NAME_MAX_CQW, min: 0, width: rect.width },
    measure,
  )
  const tracking = faceTracking(fontTable, THEME_PACK_NAME_TRACKING)

  return (
    <span
      className={cn('whitespace-nowrap text-center', GAME_TEXT_CLASS)}
      style={{
        ...getGeometryFontForLanguage(i18n.language),
        ...(specialName ? {} : { color: `#${packEntry.themePackConfig.textColor}` }),
        fontSize: `${String(fontSize)}cqw`,
        lineHeight: 0,
        position: 'relative',
        top: `${String(geometryCentreEm(fontTable, text))}em`,
        letterSpacing: `${String(tracking.letterSpacingEm)}em`,
        wordSpacing: `${String(tracking.wordSpacingEm)}em`,
        textShadow: nameShadow('themePack', i18n.language),
      }}
    >
      {specialName ? applyTextStyles(themePackCardName(specialName)) : text}
    </span>
  )
}
