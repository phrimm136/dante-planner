import { useTranslation } from 'react-i18next'
import {
  createAdvanceMeasure,
  fitFontSize,
  midlineOffsetEm,
  nameShadow,
  useFontAdvances,
  type PctRect,
} from '@/shared/cardLayout'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { parseColorTags, stripColorTags } from '@/shared/gameText'
import { useThemePackListI18n } from '../hooks/useThemePackListData'
import {
  THEME_PACK_NAME_MAX_CQW,
  THEME_PACK_NAME_TEXT,
  THEME_PACK_NAME_TRACKING,
  themePackCardName,
} from '../lib/cardLayout'
import type { ThemePackEntry } from '../types/ThemePackTypes'

interface ThemePackNameProps {
  packId: string
  packEntry: ThemePackEntry
  /** The name box, as a percentage of the card root. */
  rect: PctRect
}

/**
 * The localized name printed on a theme pack card. Suspends while the name list and the
 * display face's advance table load — the card wraps it in a name-sized boundary.
 */
export function ThemePackName({ packId, packEntry, rect }: ThemePackNameProps) {
  const { i18n } = useTranslation()
  const names = useThemePackListI18n()
  const fontTable = useFontAdvances(i18n.language)

  const entry = names[packId]
  const specialName = entry?.specialName
  const text = themePackCardName(
    specialName ? stripColorTags(specialName) : (entry?.name ?? packId),
  )

  const measure = createAdvanceMeasure(fontTable, THEME_PACK_NAME_TRACKING)
  const fontSize = fitFontSize(
    text,
    { max: THEME_PACK_NAME_MAX_CQW, min: 0, width: rect.width },
    measure,
  )

  return (
    <span
      className="whitespace-nowrap text-center"
      style={{
        ...getDisplayFontForLanguage(i18n.language),
        ...(specialName ? {} : { color: `#${packEntry.themePackConfig.textColor}` }),
        fontSize: `${String(fontSize)}cqw`,
        lineHeight: 1,
        transform: `translateY(${String(midlineOffsetEm(fontTable) * fontSize)}cqw)`,
        letterSpacing: THEME_PACK_NAME_TEXT.letterSpacing,
        wordSpacing: THEME_PACK_NAME_TEXT.wordSpacing,
        textShadow: nameShadow('themePack', i18n.language),
      }}
    >
      {specialName ? parseColorTags(themePackCardName(specialName)) : text}
    </span>
  )
}
