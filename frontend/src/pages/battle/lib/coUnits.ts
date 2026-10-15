import type { EngagementRow } from '../schemas/BattleDetailSchemas'
import type { BattleNameList } from '../schemas/BattleSpecSchemas'
import { battleListName, envyIdentityTitle } from './battleListing'
import type { BattleFamily, CoUnitRelation } from './battleVocabulary'

export interface CoUnitCardSpec {
  personalityId?: number | undefined
}

export interface CoUnitCatalog {
  specs: Readonly<Record<BattleFamily, Readonly<Record<string, CoUnitCardSpec>>>>
  names: Readonly<Record<BattleFamily, BattleNameList>>
  identityTitles: Readonly<Record<string, string>>
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
    const spec = catalog.specs[card.family][id]
    if (spec === undefined) continue
    const key = `${card.family}/${id}/${relation ?? ''}`
    if (seen.has(key)) continue
    seen.add(key)
    chips.push({
      key,
      label: battleListName(
        catalog.names[card.family][id],
        row.case,
        id,
        envyIdentityTitle(spec.personalityId, catalog.identityTitles),
      ),
      family: card.family,
      id,
      stage: row.stage,
      relation,
    })
  }
  return chips
}
