import { Fragment, useLayoutEffect, useState, type CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'
import { getAttributeColors, type ImportancePalette } from '@/shared/gameData'
import {
  faceTracking,
  fitNodeText,
  midlineOffsetEm,
  useFontAdvances,
  type TextNodeSpec,
} from '@/shared/cardLayout'
import { panelNameShadow } from '../lib/panelUnderlay'
import { importanceBannerStyle } from '@/shared/importance'
import { CLIENT_TEXT_SCALE, NAME_PLATE_UNITS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { clientLength, cn, getDisplayFontForLanguage } from '@/lib/utils'
import { Skeleton } from '@/components/ui/skeleton'

interface StyledSkillNameProps {
  name: string
  attributeType?: string
}

export interface NamePlateImportance {
  level: number
  palette: ImportancePalette
}

export type NamePlateKind = 'skill' | 'passive'

interface NamePlateProps {
  kind: NamePlateKind
  name: string
  attributeType?: string | undefined
  importance?: NamePlateImportance | undefined
}

interface NamePlateSpec {
  node: TextNodeSpec
  marginX: number
  textHeight: number
  body: CSSProperties
  textBox: CSSProperties
  midline: boolean
  bodyInsetLeft: number
}

const TEXT_COLOR = '#eecea4'

function nameNode(maxSize: number, minSize: number): TextNodeSpec {
  return {
    maxSize,
    minSize,
    letterSpacingEm: NAME_PLATE_UNITS.LETTER_SPACING_EM,
    wordSpacingEm: NAME_PLATE_UNITS.WORD_SPACING_EM,
    lineSpacingEm: 0,
  }
}

const { SKILL, PASSIVE } = NAME_PLATE_UNITS

const NAME_PLATES: Record<NamePlateKind, NamePlateSpec> = {
  skill: {
    node: nameNode(SKILL.FONT, SKILL.MIN_FONT),
    marginX: SKILL.PAD_LEFT + SKILL.PAD_RIGHT,
    textHeight: SKILL.TEXT_HEIGHT,
    body: {
      height: clientLength(NAME_PLATE_UNITS.HEIGHT),
      paddingLeft: clientLength(SKILL.PAD_LEFT),
      paddingRight: clientLength(SKILL.PAD_RIGHT),
      paddingTop: clientLength(SKILL.TEXT_TOP),
      paddingBottom: clientLength(NAME_PLATE_UNITS.HEIGHT - SKILL.TEXT_TOP - SKILL.TEXT_HEIGHT),
    },
    textBox: { display: 'flex', alignItems: 'center' },
    midline: true,
    bodyInsetLeft: SKILL.BODY_INSET_LEFT,
  },
  passive: {
    node: nameNode(PASSIVE.FONT, PASSIVE.MIN_FONT),
    marginX: PASSIVE.PAD_LEFT + PASSIVE.PAD_RIGHT,
    textHeight: PASSIVE.TEXT_HEIGHT,
    body: {
      height: clientLength(NAME_PLATE_UNITS.HEIGHT),
      paddingLeft: clientLength(PASSIVE.PAD_LEFT),
      paddingRight: clientLength(PASSIVE.PAD_RIGHT),
      paddingTop: clientLength(PASSIVE.PAD_TOP),
      paddingBottom: clientLength(0),
    },
    textBox: {},
    midline: false,
    bodyInsetLeft: 0,
  },
}

function insetBackground(image: string, insetLeft: number): CSSProperties {
  if (insetLeft === 0) return { backgroundImage: image }
  return {
    backgroundImage: image,
    backgroundSize: `calc(100% - ${clientLength(insetLeft)}) 100%`,
    backgroundPosition: 'right top',
    backgroundRepeat: 'no-repeat',
  }
}

function generateStripeGradient(color: string): string {
  return `linear-gradient(290deg,
    transparent 2.5em,
    ${color} 2.5em, ${color} 2.65em,
    transparent 2.65em, transparent 2.8em,
    ${color} 2.8em, ${color} 2.95em,
    transparent 2.95em, transparent 3.1em,
    ${color} 3.1em, ${color} 3.25em,
    transparent 3.25em, transparent 3.4em,
    ${color} 3.4em
  )`
}

function generateBackgroundGradient(darkColor: string): string {
  return `linear-gradient(255deg, transparent 1.5em, ${darkColor} 1.5em)`
}

function plateTailGradient(darkColor: string): string {
  const cut = clientLength(NAME_PLATE_UNITS.TAIL.CUT)
  return `linear-gradient(${String(NAME_PLATE_UNITS.TAIL.ANGLE)}deg, transparent ${cut}, ${darkColor} ${cut})`
}

function plateBodyGradient(color: string): string {
  const { STRIPE_ANGLE, STRIPES_START, STRIPE, BODY } = NAME_PLATE_UNITS.TAIL
  const stops: string[] = []
  for (let start = STRIPES_START; start < BODY; start += 2 * STRIPE) {
    const from = clientLength(start)
    const to = clientLength(Math.min(start + STRIPE, BODY))
    stops.push(`transparent ${from}`, `${color} ${from}`, `${color} ${to}`, `transparent ${to}`)
  }
  const body = clientLength(BODY)
  return `linear-gradient(${String(STRIPE_ANGLE)}deg, ${stops.join(', ')}, transparent ${body}, ${color} ${body})`
}

function pxPerClientUnit(): number {
  const rootPx = Number.parseFloat(getComputedStyle(document.documentElement).fontSize)
  return (rootPx * CLIENT_TEXT_SCALE.FONT_REM) / CLIENT_TEXT_SCALE.FONT_UNITS
}

function useClientWidth(): [number | undefined, (element: HTMLDivElement | null) => void] {
  const [element, setElement] = useState<HTMLDivElement | null>(null)
  const [width, setWidth] = useState<number>()

  useLayoutEffect(() => {
    if (element === null) return undefined
    const read = () => {
      const px = element.getBoundingClientRect().width
      setWidth(px > 0 ? px / pxPerClientUnit() : undefined)
    }
    read()
    const observer = new ResizeObserver(read)
    observer.observe(element)
    return () => observer.disconnect()
  }, [element])

  return [width, setElement]
}

export function NamePlate({ kind, name, attributeType, importance }: NamePlateProps) {
  const { i18n } = useTranslation()
  const table = useFontAdvances(i18n.language)
  const { primary, dark } = getAttributeColors(attributeType)
  const [areaWidth, measureRef] = useClientWidth()
  const spec = NAME_PLATES[kind]
  const fitted =
    areaWidth === undefined
      ? { fontSize: spec.node.maxSize, lines: [name] }
      : fitNodeText(name, table, spec.node, {
          width: areaWidth - spec.marginX,
          height: spec.textHeight,
        })
  const tracking = faceTracking(table, spec.node)
  const bannerStyle =
    importance === undefined
      ? undefined
      : importanceBannerStyle(importance.level, importance.palette)

  return (
    <div ref={measureRef} data-slot="name-area" style={{ width: '100%', minWidth: 0 }}>
      <div
        {...(bannerStyle !== undefined && { 'data-slot': 'importance-banner' })}
        className={cn('w-fit', bannerStyle !== undefined && 'importance-banner')}
        style={bannerStyle}
      >
        <div
          data-slot="name-plate"
          style={{
            color: `var(--name-text, ${UNIT_PANEL_COLORS.NAME})`,
            width: '100%',
            backgroundImage: plateTailGradient(`var(--name-tail, ${dark})`),
          }}
        >
          <div
            style={{
              ...spec.body,
              ...spec.textBox,
              textAlign: 'left',
              ...insetBackground(
                plateBodyGradient(`var(--name-body, ${primary})`),
                spec.bodyInsetLeft,
              ),
            }}
          >
            <span
              data-slot="name-text"
              style={{
                fontSize: clientLength(fitted.fontSize),
                ...(spec.midline && {
                  lineHeight: 1,
                  transform: `translateY(${String(midlineOffsetEm(table))}em)`,
                }),
                letterSpacing: `${String(tracking.letterSpacingEm)}em`,
                wordSpacing: `${String(tracking.wordSpacingEm)}em`,
                whiteSpace: 'nowrap',
                textShadow: panelNameShadow(i18n.language),
                ...getDisplayFontForLanguage(i18n.language),
              }}
            >
              {fitted.lines.map((line, index) => (
                <Fragment key={`${String(index)}:${line}`}>
                  {index > 0 && <br />}
                  {line}
                </Fragment>
              ))}
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export function NamePlateSkeleton({
  kind,
  attributeType,
}: {
  kind: NamePlateKind
  attributeType?: string | undefined
}) {
  const { primary, dark } = getAttributeColors(attributeType)
  const spec = NAME_PLATES[kind]

  return (
    <div className="w-fit">
      <div
        style={{
          marginBottom: '5px',
          padding: '2px',
          width: '100%',
          backgroundImage: plateTailGradient(dark),
        }}
      >
        <div
          style={{
            ...spec.body,
            fontSize: clientLength(spec.node.maxSize),
            paddingTop: '0.3em',
            ...insetBackground(plateBodyGradient(primary), spec.bodyInsetLeft),
          }}
        >
          <Skeleton className="h-5 w-20" style={{ backgroundColor: TEXT_COLOR }} />
        </div>
      </div>
    </div>
  )
}

export function StyledSkillName({ name, attributeType }: StyledSkillNameProps) {
  const { i18n } = useTranslation()
  const { primary, dark } = getAttributeColors(attributeType)
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div className="w-fit">
      <div
        data-slot="name-plate"
        style={{
          color: `var(--name-text, ${TEXT_COLOR})`,
          marginBottom: '10px',
          width: '100%',
          backgroundImage: generateBackgroundGradient(`var(--name-tail, ${dark})`),
        }}
      >
        <div
          style={{
            textShadow: '2px 2px 2px black',
            padding: '0.1em 3.75em 0 0.3em',
            textAlign: 'left',
            backgroundImage: generateStripeGradient(`var(--name-body, ${primary})`),
          }}
        >
          <span style={{ fontSize: '20px', ...displayStyle }}>{name}</span>
        </div>
      </div>
    </div>
  )
}
