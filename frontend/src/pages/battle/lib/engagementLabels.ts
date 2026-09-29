import type { ThemePackI18n } from '@/shared/themePack'
import type {
  BattleEngagement,
  BattleStageList,
  BattleStageTexts,
  StageLabel,
  StageLabelText,
} from '../schemas/BattleDetailSchemas'
import type { BattleFacetNames } from '../schemas/BattleSpecSchemas'
import { BATTLE_CASES, ENGAGEMENT_LABEL_SEPARATOR, type BattleCase } from './battleVocabulary'
import type { Translate } from './enemy/enemyFacetOptions'

export interface EngagementSources {
  stages: BattleStageList
  texts: BattleStageTexts
  facetNames: BattleFacetNames
  themePacks: ThemePackI18n
}

export interface EngagementGroup {
  battleCase: BattleCase
  labels: string[]
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

function chapterText(
  chapter: { canto?: number | undefined; number?: string | undefined; part?: string | undefined },
  t: Translate,
): string | undefined {
  if (chapter.canto !== undefined) return t('battle.chapter.canto', { n: String(chapter.canto) })
  if (chapter.number !== undefined) {
    return t('battle.chapter.intervallo', {
      number: intervalloPrefix(chapter.number, chapter.part),
    })
  }
  return undefined
}

export function stageLabelText(
  label: StageLabel,
  text: StageLabelText | undefined,
  t: Translate,
): string {
  switch (label.kind) {
    case 'canto':
      return label.node === undefined
        ? t('battle.chapter.canto', { n: String(label.canto) })
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
        chapterText(label.chapter, t),
        difficultyText(label.chapter.difficulty, t),
        text?.dungeonNode,
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
      return t('battle.chapter.tutorial', {})
  }
}

export function engagementPlace(
  engagement: BattleEngagement,
  sources: EngagementSources,
  t: Translate,
): string {
  const stage = sources.stages[engagement.stage]
  const texts = sources.texts[engagement.stage]
  if (engagement.label !== undefined) {
    const label = stage?.labels?.[engagement.label]
    if (label !== undefined) {
      return stageLabelText(label, texts?.labelTexts?.[engagement.label], t)
    }
  }
  if (engagement.themePack !== undefined) {
    return sources.themePacks[engagement.themePack]?.name ?? engagement.themePack
  }
  if (engagement.eventId !== undefined) {
    return sources.facetNames.events[engagement.eventId] ?? engagement.eventId
  }
  if (engagement.hiddenBattle === true) return t('battle.facet.hidden', {})
  return texts?.title ?? engagement.stage
}

export function engagementLabel(
  engagement: BattleEngagement,
  sources: EngagementSources,
  t: Translate,
): string {
  const place = engagementPlace(engagement, sources, t)
  if (engagement.level === null) return place
  return t('battle.detail.engagement', {
    place,
    level: String(engagement.level),
  })
}

export function groupEngagements(
  engagements: readonly BattleEngagement[],
  sources: EngagementSources,
  t: Translate,
): EngagementGroup[] {
  const byCase = new Map<BattleCase, Set<string>>()
  for (const engagement of engagements) {
    const labels = byCase.get(engagement.case) ?? new Set<string>()
    labels.add(engagementLabel(engagement, sources, t))
    byCase.set(engagement.case, labels)
  }
  return BATTLE_CASES.flatMap((battleCase) => {
    const labels = byCase.get(battleCase)
    return labels === undefined ? [] : [{ battleCase, labels: [...labels] }]
  })
}
