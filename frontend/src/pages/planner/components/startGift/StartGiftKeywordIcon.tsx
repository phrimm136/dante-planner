import { getKeywordIconPath } from '@/shared/assets'
import { KEYWORD_ICON_CARD, pct } from '../../lib/cardLayout'

interface StartGiftKeywordIconProps {
  keyword: string
  title?: string
}

export function StartGiftKeywordIcon({ keyword, title }: StartGiftKeywordIconProps) {
  return (
    <div className="w-full aspect-square flex items-center justify-center">
      <img
        src={getKeywordIconPath(keyword)}
        alt={keyword}
        style={{ width: pct(KEYWORD_ICON_CARD.icon), height: pct(KEYWORD_ICON_CARD.icon) }}
        className="object-contain"
        {...(title !== undefined && { title })}
      />
    </div>
  )
}
