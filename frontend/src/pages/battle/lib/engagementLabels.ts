import { BATTLE_EVENT_PLACE } from '@/lib/constants'
import type { ThemePackI18n } from '@/shared/themePack'
import type {
  BattleStageList,
  EngagementRow,
  BattleStageTexts,
  StageLabel,
  StageLabelText,
} from '../schemas/BattleDetailSchemas'
import type { BattleFacetNames } from '../schemas/BattleSpecSchemas'
import {
  ENGAGEMENT_LABEL_SEPARATOR,
  ENGAGEMENT_PLACE_JOINER,
  PROLOGUE_CANTO,
  RPG_FLOOR_SUFFIX,
  RPG_ROUTE_LETTER_LENGTH,
} from './battleVocabulary'
import { cantoLabel, type Translate } from './enemy/enemyFacetOptions'
import type { SelectorEntry } from './engagementRows'

export interface EngagementSources {
  stages: BattleStageList
  texts: BattleStageTexts
  facetNames: BattleFacetNames
  themePacks: ThemePackI18n
}

function joined(...parts: (string | undefined)[]): string {
  return parts.filter((part) => part !== undefined && part !== '').join(ENGAGEMENT_LABEL_SEPARATOR)
}

function difficultyText(difficulty: string | undefined, t: Translate): string | undefined {
  if (difficulty === undefined) return undefined
  return t(`battle.difficulty.${difficulty.toLowerCase()}`, {})
}

function intervalloPrefix(number: string, part: string | undefined): string {
  return part === undefined ? number : `${number}-${part}`
}

type DungeonChapter = Extract<StageLabel, { kind: 'storyDungeon' }>['chapter']

function chapterPrefix(chapter: DungeonChapter): string {
  return 'canto' in chapter ? String(chapter.canto) : intervalloPrefix(chapter.number, chapter.part)
}

export function eventPlaceText(text: string): string {
  const codePoints = Array.from(text)
  if (codePoints.length <= BATTLE_EVENT_PLACE.MAX_CODE_POINTS) return text
  return `${codePoints.slice(0, BATTLE_EVENT_PLACE.MAX_CODE_POINTS).join('')}${BATTLE_EVENT_PLACE.ELLIPSIS}`
}

export function stageLabelText(
  label: StageLabel,
  text: StageLabelText | undefined,
  t: Translate,
): string {
  switch (label.kind) {
    case 'canto':
      return label.node === undefined
        ? cantoLabel(label.canto, t)
        : `${String(label.canto)}-${String(label.node)}`
    case 'intervallo': {
      const prefix = intervalloPrefix(label.number, label.part)
      const place =
        label.node === undefined
          ? t('battle.chapter.intervallo', { number: prefix })
          : `${prefix}-${String(label.node)}`
      return joined(place, difficultyText(label.difficulty, t))
    }
    case 'walpurgis':
    case 'refractral':
      return joined(text?.title, difficultyText(label.difficulty, t))
    case 'storyDungeon':
      return joined(
        `${chapterPrefix(label.chapter)}-${String(label.stage)}`,
        difficultyText(label.chapter.difficulty, t),
      )
    case 'railway': {
      const line = text?.line ?? String(label.line)
      return joined(label.rerun ? t('battle.rerun', { line }) : line, text?.node)
    }
    case 'luxcavation':
      return text?.dungeon ?? t('battle.luxcavation.exp', {})
    case 'aprilFools':
      return text?.title ?? label.titleKey
    case 'tutorial':
      return cantoLabel(PROLOGUE_CANTO, t)
    case 'rpg': {
      const place = `${String(label.canto)}-${label.route.slice(0, RPG_ROUTE_LETTER_LENGTH)}`
      return label.floor === undefined ? place : `${place} ${label.floor}${RPG_FLOOR_SUFFIX}`
    }
  }
}

function mirrorPlaces(
  stage: BattleStageList[string] | undefined,
  sources: EngagementSources,
  t: Translate,
): string[] {
  const mirror = stage?.mirror
  if (mirror === undefined) return []
  return [
    ...(mirror.themeIds ?? []).flatMap((id) => sources.themePacks[String(id)]?.name ?? []),
    ...(mirror.eventIds ?? []).flatMap((id) => {
      const event = sources.facetNames.events[id]
      return event === undefined ? [] : [eventPlaceText(event)]
    }),
    ...(mirror.hiddenBattle === true ? [t('battle.facet.hidden', {})] : []),
  ]
}

export function engagementPlace(
  row: EngagementRow,
  sources: EngagementSources,
  t: Translate,
): string {
  const stage = sources.stages[row.stage]
  const texts = sources.texts[row.stage]
  const places = [
    ...row.label.flatMap((index) => {
      const label = stage?.labels?.[index]
      return label === undefined ? [] : [stageLabelText(label, texts?.labelTexts?.[index], t)]
    }),
    ...(row.label.length === 0 ? mirrorPlaces(stage, sources, t) : []),
  ]
  if (places.length > 0) return places.join(ENGAGEMENT_PLACE_JOINER)
  return texts?.title ?? t(`battle.case.${row.case}`, {})
}

export function engagementLabel(
  row: EngagementRow,
  sources: EngagementSources,
  t: Translate,
): string {
  const place = engagementPlace(row, sources, t)
  if (row.level === null) return place
  return t('battle.detail.engagement', {
    place,
    level: String(row.level),
  })
}

export function suffixedLabel({ label, suffix }: SelectorEntry, t: Translate): string {
  if (suffix === undefined) return label
  return `${label} ${t('battle.detail.rowSuffix', { n: String(suffix) })}`
}
