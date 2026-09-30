import { getCoinDescIconPath } from '@/shared/assets'
import { type SkillDescEntry } from '@/shared/gameData'
import { FormattedDescription } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { PANEL_TEXT_NODES, SKILL_LOWER_UNITS, UNIT_PANEL_COLORS } from '@/lib/constants'
import { clientLength } from '@/lib/utils'
import { textBlockStyle, textBlockTrim, trimmedLength } from '../lib/textBlock'

const { COIN_LIST, FLAVOR } = SKILL_LOWER_UNITS
const { SKILL_DESCRIPTION, COIN_TEXT, SKILL_FLAVOR } = PANEL_TEXT_NODES

const descriptionStyle = {
  ...textBlockStyle(SKILL_DESCRIPTION, UNIT_PANEL_COLORS.DESCRIPTION),
  marginTop: textBlockTrim(SKILL_DESCRIPTION),
  marginBottom: textBlockTrim(SKILL_DESCRIPTION),
}

const coinTextBodyStyle = {
  ...textBlockStyle(COIN_TEXT, UNIT_PANEL_COLORS.COIN_TEXT),
  marginTop: textBlockTrim(COIN_TEXT),
  marginBottom: textBlockTrim(COIN_TEXT),
}

const coinListStyle = {
  paddingTop: clientLength(COIN_LIST.PAD_TOP),
  paddingBottom: clientLength(COIN_LIST.PAD_BOTTOM),
}

const coinRowStyle = { minHeight: clientLength(COIN_LIST.SLOT_MIN) }

const coinIconStyle = {
  left: clientLength(COIN_LIST.ICON_LEFT),
  top: 0,
  width: clientLength(COIN_LIST.ICON_SIZE),
  height: clientLength(COIN_LIST.ICON_SIZE),
}

const coinTextStyle = {
  padding: [COIN_LIST.TEXT_TOP, COIN_LIST.TEXT_RIGHT, COIN_LIST.TEXT_BOTTOM, COIN_LIST.TEXT_LEFT]
    .map(clientLength)
    .join(' '),
}

const flavorStyle = {
  ...textBlockStyle(SKILL_FLAVOR, UNIT_PANEL_COLORS.FLAVOR),
  fontSize: clientLength(FLAVOR.FONT),
  marginTop: trimmedLength(FLAVOR.TOP, SKILL_FLAVOR, FLAVOR.FONT),
  marginBottom: trimmedLength(FLAVOR.BOTTOM, SKILL_FLAVOR, FLAVOR.FONT),
}

interface SkillDescriptionProps {
  descData: SkillDescEntry
  flavor?: string
}

export function SkillDescription({ descData, flavor }: SkillDescriptionProps) {
  const { desc, coinDescs } = descData
  const coinSlots = (coinDescs ?? [])
    .map((text, index) => ({ text, index }))
    .filter((slot) => slot.text)

  return (
    <div className="text-sm">
      <div data-slot="skill-desc" style={desc ? descriptionStyle : undefined}>
        <FormattedDescription text={desc ?? ''} />
      </div>

      {coinSlots.length > 0 && (
        <div data-slot="skill-coin-list" style={coinListStyle}>
          {coinSlots.map(({ text, index }) => (
            <div key={index} className="relative" style={coinRowStyle}>
              <img
                src={getCoinDescIconPath(index)}
                alt={`Coin ${index + 1}`}
                className="absolute"
                style={coinIconStyle}
              />
              <div style={coinTextStyle}>
                <div data-slot="skill-coin-text" style={coinTextBodyStyle}>
                  <FormattedDescription text={text} />
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {flavor && (
        <p data-testid="skill-flavor" className="italic whitespace-pre-line" style={flavorStyle}>
          {flavor}
        </p>
      )}
    </div>
  )
}

export function SkillDescriptionSkeleton() {
  return (
    <div className="text-sm space-y-2">
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
    </div>
  )
}

export function getMergedSkillDesc(descs: SkillDescEntry[], level: number): SkillDescEntry {
  const merged: SkillDescEntry = {}
  for (let i = 0; i < level; i++) {
    const current = descs[i]
    if (!current) continue
    if (current.desc !== undefined && current.desc !== '') {
      merged.desc = current.desc
    }
    if (current.coinDescs && current.coinDescs.length > 0) {
      merged.coinDescs = current.coinDescs
    }
  }
  return merged
}
