import { useTranslation } from 'react-i18next'

import {
  createAdvanceMeasure,
  fitFontSize,
  useFontAdvances,
  wrapText,
  type TrackingSpec,
} from '@/shared/cardLayout'
import { getDisplayFontForLanguage, getLineHeightForLanguage } from '@/lib/utils'
import { START_BUFF_CARD, START_BUFF_MINI_CARD } from '../../lib/cardLayout'

/** The start buff faces are drawn with the table's own advances, untracked. */
const TRACKING: TrackingSpec = { letterSpacingEm: 0, wordSpacingEm: 0 }

interface StartBuffNameProps {
  text: string
  /** The name's ceiling on this artwork, in cqw of the card root */
  maxSize: number
  color: string | undefined
  shadow?: string | undefined
}

/**
 * The start buff card's name, on one line, scaled down until it fits its box.
 *
 * Reads the display face's advance table through `useSuspenseQuery` — render it inside a
 * Suspense boundary.
 */
export function StartBuffName({ text, maxSize, color, shadow }: StartBuffNameProps) {
  const { i18n } = useTranslation()
  const fontTable = useFontAdvances(i18n.language)
  const measure = createAdvanceMeasure(fontTable, TRACKING)

  const fontSize = fitFontSize(
    text,
    { max: maxSize, min: 0, width: START_BUFF_CARD.nameWidth },
    measure,
  )

  return (
    <span
      className="text-center"
      style={{
        ...getDisplayFontForLanguage(i18n.language),
        display: 'block',
        fontSize: `${String(fontSize)}cqw`,
        whiteSpace: 'nowrap',
        color,
        ...(shadow !== undefined && { textShadow: shadow }),
      }}
    >
      {text}
    </span>
  )
}

interface StartBuffMiniNameProps {
  text: string
  color: string | undefined
}

/**
 * The summary mini card's name, wrapped into lines at a fixed size.
 *
 * Reads the display face's advance table through `useSuspenseQuery` — render it inside a
 * Suspense boundary.
 */
export function StartBuffMiniName({ text, color }: StartBuffMiniNameProps) {
  const { i18n } = useTranslation()
  const fontTable = useFontAdvances(i18n.language)
  const measure = createAdvanceMeasure(fontTable, TRACKING)

  const fontSize = START_BUFF_MINI_CARD.nameSize
  const { lines } = wrapText(text, fontSize, { width: START_BUFF_MINI_CARD.nameWidth }, measure)

  return (
    <span
      className="text-center block"
      style={{
        ...getDisplayFontForLanguage(i18n.language),
        fontSize: `${String(fontSize)}cqw`,
        lineHeight: getLineHeightForLanguage(i18n.language),
        color,
      }}
    >
      {lines.slice(0, START_BUFF_MINI_CARD.nameMaxLines).map((line, index) => (
        <span key={`${String(index)}:${line}`} style={{ display: 'block', whiteSpace: 'nowrap' }}>
          {line}
        </span>
      ))}
    </span>
  )
}
