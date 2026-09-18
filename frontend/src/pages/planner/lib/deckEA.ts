import {
  AFFINITIES,
  DEFAULT_SKILL_EA,
  EGO_KEYWORD_GRANTS,
  GIFT_KEYWORD_GRANTS,
  KEYWORD_GRANT_MIN_THREADSPIN,
  OFFENSIVE_SKILL_SLOTS,
  STATUS_EFFECTS,
} from '@/shared/gameData'
import { getBaseGiftId } from '@/pages/egoGift'
import type { EGOGiftId, EncodedGiftId } from '@/shared/gameData'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { AffinityCount, DeckState, KeywordCount, SinnerEquipment } from '../types/DeckTypes'

export interface IdentityEASpec {
  attributeType?: readonly string[]
  skillKeywordList?: readonly string[]
}

export interface EGOEASpec {
  requirements?: Readonly<Record<string, number>>
}

export type KeywordEACount = KeywordCount & {
  deployedCount: number
  allCount: number
}

/** Deployment order holds 0-based sinner indices; equipment is keyed 1-based */
function sinnerCodesOf(deploymentOrder: readonly number[]): string[] {
  return deploymentOrder.map((index) => String(index + 1))
}

export function collectOwnedGiftIds(source: {
  selectedGiftIds: ReadonlySet<EncodedGiftId>
  observationGiftIds: ReadonlySet<EncodedGiftId>
  comprehensiveGiftIds: ReadonlySet<EncodedGiftId>
  floorSelections: readonly FloorThemeSelection[]
}): ReadonlySet<EGOGiftId> {
  return new Set(
    [
      ...source.selectedGiftIds,
      ...source.observationGiftIds,
      ...source.comprehensiveGiftIds,
      ...source.floorSelections.flatMap((floor) => [...floor.giftIds]),
    ].map(getBaseGiftId),
  )
}

function grantedKeywords(
  equipment: SinnerEquipment,
  ownedGiftIds: ReadonlySet<EGOGiftId>,
): string[] {
  const grants = [
    ...Object.values(equipment.egos).flatMap((ego) =>
      ego && ego.threadspin >= KEYWORD_GRANT_MIN_THREADSPIN ? [EGO_KEYWORD_GRANTS.get(ego.id)] : [],
    ),
    ...[...ownedGiftIds].map((giftId) => GIFT_KEYWORD_GRANTS.get(giftId)),
  ]
  return grants.flatMap((entry) =>
    entry && entry.identityId === equipment.identity.id ? [entry.keyword] : [],
  )
}

export function computeAffinityEA(
  deckState: DeckState,
  identitySpec: Record<string, IdentityEASpec>,
  egoSpec: Record<string, EGOEASpec>,
): AffinityCount[] {
  const counts: AffinityCount[] = AFFINITIES.map((affinity) => ({
    affinity,
    generated: 0,
    consumed: 0,
  }))
  const byAffinity = new Map<string, AffinityCount>(counts.map((entry) => [entry.affinity, entry]))

  sinnerCodesOf(deckState.deploymentOrder).forEach((sinnerCode) => {
    const equipment = deckState.equipment[sinnerCode]
    if (!equipment) return

    const attributeType = identitySpec[equipment.identity.id]?.attributeType
    OFFENSIVE_SKILL_SLOTS.forEach((slot) => {
      const affinity = attributeType?.[slot]
      if (affinity === undefined) return

      const entry = byAffinity.get(affinity)
      if (entry) entry.generated += DEFAULT_SKILL_EA[slot]
    })

    Object.values(equipment.egos).forEach((equippedEgo) => {
      if (!equippedEgo) return

      const requirements = egoSpec[equippedEgo.id]?.requirements
      if (!requirements) return

      Object.entries(requirements).forEach(([affinity, cost]) => {
        const entry = byAffinity.get(affinity)
        if (entry && cost > 0) entry.consumed += cost
      })
    })
  })

  return counts
}

export function computeKeywordEA(
  deckState: DeckState,
  identitySpec: Record<string, IdentityEASpec>,
  ownedGiftIds: ReadonlySet<EGOGiftId>,
): KeywordEACount[] {
  const tallies: KeywordEACount[] = STATUS_EFFECTS.map((keyword) => ({
    keyword,
    count: 0,
    deployedCount: 0,
    allCount: 0,
  }))
  const byKeyword = new Map<string, KeywordEACount>(tallies.map((entry) => [entry.keyword, entry]))

  const tally = (sinnerCodes: string[], scope: 'deployedCount' | 'allCount') => {
    sinnerCodes.forEach((sinnerCode) => {
      const equipment = deckState.equipment[sinnerCode]
      if (!equipment) return

      const base = identitySpec[equipment.identity.id]?.skillKeywordList ?? []
      const granted = grantedKeywords(equipment, ownedGiftIds).filter(
        (keyword) => !base.includes(keyword),
      )
      for (const keyword of [...base, ...granted]) {
        const entry = byKeyword.get(keyword)
        if (entry) entry[scope] += 1
      }
    })
  }

  const allSinnerCodes = sinnerCodesOf(deckState.deploymentOrder)
  tally(allSinnerCodes.slice(0, deckState.deploymentConfig.maxDeployed), 'deployedCount')
  tally(allSinnerCodes, 'allCount')

  return tallies.map((entry) => ({ ...entry, count: entry.allCount }))
}
