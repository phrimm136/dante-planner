import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getStartBuffSpritePath } from '@/shared/assets'
import { getDisplayFontForLanguage, getDisplayFontForNumeric } from '@/lib/utils'
import { aspectOf, type CardSizePx } from '@/shared/cardLayout'
import type { StartBuff, StartBuffI18n, BattleKeywords, EnhancementLevel } from '@/shared/gameText'
import { colorRuns, getEnhancementSuffix, createBuffId } from '@/shared/gameText'
import type { LayoutElement, StartBuffCardLayout } from '../../schemas/StartBuffCardSchemas'
import {
  costColor,
  fitSpace,
  imageStyle,
  shownCardElements,
  spriteFile,
  type CardState,
} from '../../lib/startBuffCardLayout'
import { useStoreSeason } from '../../stores/usePlannerStore'
import { buffDescriptionText } from './formatBuffDescription'
import { CardSprite } from './CardSprite'
import { CardText } from './StartBuffName'
import { StartBuffDescription } from './StartBuffDescription'

interface StartBuffCardProps {
  layout: StartBuffCardLayout
  slot: CardSizePx
  buff: StartBuff
  allBuffs: StartBuff[]
  i18n: StartBuffI18n
  battleKeywords?: BattleKeywords
  isSelected: boolean
  onSelect: (buffId: number, selected: boolean) => void
  enhancement: EnhancementLevel
  onEnhancementChange: (baseId: number, level: EnhancementLevel) => void
}

function buttonLevel(buttonIndex: number): 1 | 2 {
  const level = buttonIndex + 1
  if (level === 1 || level === 2) return level
  throw new Error(`start-buff level button ${String(buttonIndex)} has no enhancement level`)
}

export function StartBuffCard({
  layout,
  slot,
  buff,
  allBuffs,
  i18n,
  battleKeywords,
  isSelected,
  onSelect,
  enhancement,
  onEnhancementChange,
}: StartBuffCardProps) {
  const { t, i18n: i18next } = useTranslation('database')
  const { contentVersion } = useStoreSeason()
  const language = i18next.language

  const [hovered, setHovered] = useState(false)
  const [hoveredButton, setHoveredButton] = useState<number | null>(null)

  const space = fitSpace(layout.canvas, slot)
  const currentBuffId = createBuffId(buff.baseId, enhancement)
  const displayBuff = allBuffs.find((b) => Number(b.id) === currentBuffId) ?? buff

  const runs = colorRuns(
    buffDescriptionText(
      displayBuff.effects,
      i18n,
      layout.description.bullet.prefix,
      battleKeywords,
    ),
  )

  const state: CardState = {
    selected: isSelected,
    hovered,
    hoveredButton,
    enhancement,
    overflows: false,
  }

  const toggleSelected = () => {
    onSelect(currentBuffId, !isSelected)
  }

  const renderElement = (element: LayoutElement, key: string) => {
    switch (element.role) {
      case 'descViewport':
        return (
          <StartBuffDescription
            key={key}
            version={contentVersion}
            layout={layout}
            runs={runs}
            language={language}
            state={state}
            baseId={buff.baseId}
            space={space}
            onClick={toggleSelected}
          />
        )
      case 'desc':
        return null
      case 'name':
        return (
          <CardText
            key={key}
            text={`${displayBuff.name}${getEnhancementSuffix(enhancement)}`}
            element={element}
            tableRole="name"
            language={language}
            space={space}
            color={element.text.color}
            fontFamily={getDisplayFontForLanguage(language).fontFamily}
            testId="start-buff-name"
          />
        )
      case 'cost':
        return (
          <CardText
            key={key}
            text={String(displayBuff.cost)}
            element={element}
            tableRole="cost"
            language={language}
            space={space}
            color={costColor(layout, displayBuff.level)}
            fontFamily={getDisplayFontForNumeric()}
            testId="start-buff-cost"
          />
        )
      case 'levelButton': {
        const level = buttonLevel(element.buttonIndex)
        const file = spriteFile(element.image, buff.baseId)
        if (file === null) return null
        const src = getStartBuffSpritePath(contentVersion, file)
        return (
          <button
            key={key}
            type="button"
            data-sprite={src}
            aria-label={`${t('tierLabel.enhancement')} ${String(level)}`}
            aria-pressed={enhancement === level}
            style={imageStyle(element.image, element.rect, space, src)}
            onClick={() => {
              onEnhancementChange(buff.baseId, enhancement === level ? 0 : level)
            }}
            onMouseEnter={() => {
              setHoveredButton(element.buttonIndex)
            }}
            onMouseLeave={() => {
              setHoveredButton(null)
            }}
          />
        )
      }
      default: {
        const file = spriteFile(element.image, buff.baseId)
        if (file === null) return null
        return (
          <CardSprite
            key={key}
            version={contentVersion}
            file={file}
            image={element.image}
            rect={element.rect}
            space={space}
          />
        )
      }
    }
  }

  return (
    <div
      className="relative w-full cursor-pointer"
      style={{ aspectRatio: String(aspectOf(slot)) }}
      onMouseEnter={() => {
        setHovered(true)
      }}
      onMouseLeave={() => {
        setHovered(false)
      }}
    >
      <button
        type="button"
        className="absolute inset-0"
        aria-label={displayBuff.name}
        aria-pressed={isSelected}
        onClick={toggleSelected}
      />
      {shownCardElements(layout, state).map((element) => renderElement(element, element.path))}
    </div>
  )
}
