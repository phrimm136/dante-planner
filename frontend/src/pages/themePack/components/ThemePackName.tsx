import { useTranslation } from 'react-i18next'
import { AutoSizeText } from '@/components/ui/AutoSizeText'
import { getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { parseColorTags, stripColorTags } from '@/shared/gameText'
import { useThemePackListI18n } from '../hooks/useThemePackListData'
import { isExtremePack } from '../types/ThemePackTypes'
import type { ThemePackEntry } from '../types/ThemePackTypes'

interface ThemePackNameProps {
  packId: string
  packEntry: ThemePackEntry
}

/**
 * The localized name printed on a theme pack card. Suspends while the name
 * list loads — the card wraps it in a name-sized boundary.
 */
export function ThemePackName({ packId, packEntry }: ThemePackNameProps) {
  const { i18n } = useTranslation()
  const names = useThemePackListI18n()
  const entry = names[packId]
  const specialName = entry?.specialName
  const isExtreme = isExtremePack(packEntry)

  return (
    <AutoSizeText
      text={specialName ? stripColorTags(specialName) : (entry?.name ?? packId)}
      width={!isExtreme ? 168 : 154}
      className="text-center"
      style={{
        ...getDisplayFontForLanguage(i18n.language),
        ...(!specialName && { color: `#${packEntry.themePackConfig.textColor}` }),
        filter: 'drop-shadow(1.2px 1.2px 0 rgba(0,0,0,0.9))',
      }}
      minFontSize={15}
      maxFontSize={24}
      lineHeight={getLineHeightForLanguage(i18n.language)}
      coloredContent={specialName ? parseColorTags(specialName) : undefined}
    />
  )
}
