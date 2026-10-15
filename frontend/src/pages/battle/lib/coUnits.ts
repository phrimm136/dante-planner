import type { EngagementRow } from '../schemas/BattleDetailSchemas'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import type { AssistantSpec } from '../types/AssistantTypes'
import type { EnemySpec } from '../types/EnemyTypes'
import type { SinnerEventSpec } from '../types/SinnerEventTypes'
import { battleCardName, battleListName, envyIdentityTitle } from './battleListing'
import type { BattleFamily, CoUnitRelation } from './battleVocabulary'

export interface CoUnitCatalog {
  specs: {
    enemy: Readonly<Record<string, EnemySpec>>
    assistant: Readonly<Record<string, AssistantSpec>>
    sinnerEvent: Readonly<Record<string, SinnerEventSpec>>
  }
  names: Readonly<Record<BattleFamily, BattleNameList>>
  identityTitles: Readonly<Record<string, string>>
  eventTitle: (family: BattleFamily, id: string) => string | undefined
}

export interface CoUnitChip {
  key: string
  label: string
  family: BattleFamily
  id: string
  stage: string
  relation: CoUnitRelation | undefined
}

export interface CoUnitCard {
  family: BattleFamily
  id: string
}

export function coUnitChips(
  row: EngagementRow,
  catalog: CoUnitCatalog,
  current: CoUnitCard,
): CoUnitChip[] {
  const chips: CoUnitChip[] = []
  const seen = new Set<string>()
  for (const { card, relation } of row.coUnits) {
    const id = String(card.id)
    if (card.family === current.family && id === current.id) continue
    if (catalog.specs[card.family][id] === undefined) continue
    const key = `${card.family}/${id}/${relation ?? ''}`
    if (seen.has(key)) continue
    seen.add(key)
    chips.push({
      key,
      label: battleCardName(
        battleListName(
          catalog.names[card.family][id],
          row.case,
          id,
          envyIdentityTitle(
            card.family === 'enemy' ? catalog.specs.enemy[id]?.personalityId : undefined,
            catalog.identityTitles,
          ),
        ),
        catalog.eventTitle(card.family, id),
      ),
      family: card.family,
      id,
      stage: row.stage,
      relation,
    })
  }
  return chips
}
