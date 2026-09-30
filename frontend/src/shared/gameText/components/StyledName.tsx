import { Fragment, useId, useLayoutEffect, useState, type CSSProperties } from 'react'
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
import { namePlateSliceStyles, tintColorMatrix, type NamePlateKind } from '../lib/namePlate'
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

interface NamePlateProps {
  kind: NamePlateKind
  name: string
  attributeType?: string | undefined
  importance?: NamePlateImportance | undefined
}

interface PlateSlice {
  LEFT: number
  RIGHT: number
  SPRITE_WIDTH: number
}

interface NamePlateSpec {
  node: TextNodeSpec
  marginX: number
  textWidthMax: number
  textHeight: number
  body: CSSProperties
  slice: PlateSlice
  tinted: boolean
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

const PASSIVE_TEXT_INSET = Number(((PASSIVE.HEIGHT - PASSIVE.TEXT_HEIGHT) / 2).toFixed(3))

const NAME_PLATES: Record<NamePlateKind, NamePlateSpec> = {
  skill: {
    node: nameNode(SKILL.FONT, SKILL.MIN_FONT),
    marginX: SKILL.PAD_LEFT + SKILL.PAD_RIGHT,
    textWidthMax: SKILL.TEXT_WIDTH_MAX,
    textHeight: SKILL.TEXT_HEIGHT,
    body: {
      height: clientLength(SKILL.HEIGHT),
      paddingLeft: clientLength(SKILL.PAD_LEFT),
      paddingRight: clientLength(SKILL.PAD_RIGHT),
      paddingTop: clientLength(0),
      paddingBottom: clientLength(SKILL.TEXT_MARGIN_BOTTOM),
    },
    slice: SKILL.SLICE,
    tinted: true,
  },
  passive: {
    node: nameNode(PASSIVE.FONT, PASSIVE.MIN_FONT),
    marginX: PASSIVE.PAD_LEFT + PASSIVE.PAD_RIGHT,
    textWidthMax: PASSIVE.TEXT_WIDTH_MAX,
    textHeight: PASSIVE.TEXT_HEIGHT,
    body: {
      height: clientLength(PASSIVE.HEIGHT),
      paddingLeft: clientLength(PASSIVE.PAD_LEFT),
      paddingRight: clientLength(PASSIVE.PAD_RIGHT),
      paddingTop: clientLength(PASSIVE_TEXT_INSET),
      paddingBottom: clientLength(PASSIVE_TEXT_INSET),
    },
    slice: PASSIVE.SLICE,
    tinted: false,
  },
}

const TEXT_BOX: CSSProperties = { display: 'flex', alignItems: 'center' }

function PlateSlices({ kind, tint }: { kind: NamePlateKind; tint: string | undefined }) {
  const filterId = useId()
  const pieces = namePlateSliceStyles(kind, tint === undefined ? undefined : filterId)
  return (
    <div
      data-slot="name-plate-slices"
      aria-hidden
      style={{ position: 'absolute', inset: 0, zIndex: -1, display: 'flex', pointerEvents: 'none' }}
    >
      {tint !== undefined && (
        <svg
          data-slot="name-plate-tint"
          aria-hidden
          width={0}
          height={0}
          style={{ position: 'absolute' }}
        >
          <filter id={filterId} colorInterpolationFilters="sRGB">
            <feColorMatrix type="matrix" values={tintColorMatrix(tint)} />
          </filter>
        </svg>
      )}
      <div data-slot="name-plate-left" style={pieces.left} />
      <div data-slot="name-plate-centre" style={pieces.centre} />
      <div data-slot="name-plate-right" style={pieces.right} />
    </div>
  )
}

function plateFrame(kind: NamePlateKind): CSSProperties {
  const { LEFT, RIGHT } = NAME_PLATES[kind].slice
  return {
    position: 'relative',
    isolation: 'isolate',
    width: '100%',
    minWidth: clientLength(LEFT + RIGHT),
  }
}

function plateTint(
  kind: NamePlateKind,
  primary: string,
  bannerStyle: CSSProperties | undefined,
): string | undefined {
  if (bannerStyle !== undefined) {
    const body: unknown = (bannerStyle as Record<string, unknown>)['--name-body']
    return typeof body === 'string' ? body : undefined
  }
  return NAME_PLATES[kind].tinted ? primary : undefined
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
  const { primary } = getAttributeColors(attributeType)
  const [areaWidth, measureRef] = useClientWidth()
  const spec = NAME_PLATES[kind]
  const fitted =
    areaWidth === undefined
      ? { fontSize: spec.node.maxSize, lines: [name] }
      : fitNodeText(name, table, spec.node, {
          width: Math.min(areaWidth, spec.textWidthMax) - spec.marginX,
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
          style={{ ...plateFrame(kind), color: `var(--name-text, ${UNIT_PANEL_COLORS.NAME})` }}
        >
          <div style={{ ...spec.body, ...TEXT_BOX, textAlign: 'left' }}>
            <span
              data-slot="name-text"
              style={{
                fontSize: clientLength(fitted.fontSize),
                lineHeight: 1,
                transform: `translateY(${String(midlineOffsetEm(table))}em)`,
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
          <PlateSlices kind={kind} tint={plateTint(kind, primary, bannerStyle)} />
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
  const { primary } = getAttributeColors(attributeType)
  const spec = NAME_PLATES[kind]

  return (
    <div className="w-fit">
      <div style={plateFrame(kind)}>
        <div style={{ ...spec.body, ...TEXT_BOX, fontSize: clientLength(spec.node.maxSize) }}>
          <Skeleton className="h-5 w-20" style={{ backgroundColor: TEXT_COLOR }} />
        </div>
        <PlateSlices kind={kind} tint={plateTint(kind, primary, undefined)} />
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
