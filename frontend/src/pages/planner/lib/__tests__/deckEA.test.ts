/**
 * deckEA.test.ts
 *
 * Unit tests for the deck EA tallies: affinity generation weighting, EGO
 * consumption, and status effect keyword counts across roster scopes.
 */

import { describe, it, expect } from 'vitest'
import { AFFINITIES, STATUS_EFFECTS } from '@/shared/gameData'
import type { EGOGiftId, EGOId, IdentityId } from '@/shared/gameData'
import { asEGOGiftId, asEGOId, asIdentityId } from '@/test-utils/fixtures'
import { computeAffinityEA, computeKeywordEA } from '../deckEA'
import type { EGOEASpec, IdentityEASpec } from '../deckEA'
import type {
  AffinityCount,
  DeckState,
  SinnerEquipment,
  ThreadspinTier,
} from '../../types/DeckTypes'

const IDENTITY_A = asIdentityId('10101')
const IDENTITY_B = asIdentityId('10201')
const IDENTITY_UNKNOWN = asIdentityId('11299')
const EGO_A = asEGOId('20101')
const EGO_B = asEGOId('20102')
const EGO_MISSING = asEGOId('20199')
const IDENTITY_10508 = asIdentityId('10508')
const IDENTITY_11009 = asIdentityId('11009')
const EGO_20509 = asEGOId('20509')
const GIFT_9282 = asEGOGiftId('9282')
const NO_GIFTS: ReadonlySet<EGOGiftId> = new Set()

const sinner = (identityId: IdentityId, egoIds: EGOId[] = []): SinnerEquipment => ({
  identity: { id: identityId, uptie: 4, level: 45 },
  egos: Object.fromEntries(
    egoIds.map((id, index) => [
      (['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'] as const)[index],
      { id, threadspin: 4 as const },
    ]),
  ),
})

const deck = (
  equipment: Record<string, SinnerEquipment>,
  deploymentOrder: number[],
  maxDeployed = 7,
): DeckState => ({ equipment, deploymentOrder, deploymentConfig: { maxDeployed } })

const generatedOf = (counts: AffinityCount[]): Record<string, number> =>
  Object.fromEntries(counts.map(({ affinity, generated }) => [affinity, generated]))

const consumedOf = (counts: AffinityCount[]): Record<string, number> =>
  Object.fromEntries(counts.map(({ affinity, consumed }) => [affinity, consumed]))

const emptySpec: Record<string, IdentityEASpec> = {}
const emptyEgoSpec: Record<string, EGOEASpec> = {}

describe('computeAffinityEA', () => {
  it('returns every affinity at zero for an empty deck', () => {
    const result = computeAffinityEA(deck({}, []), emptySpec, emptyEgoSpec)

    expect(result.map((entry) => entry.affinity)).toEqual([...AFFINITIES])
    expect(result.every(({ generated, consumed }) => generated === 0 && consumed === 0)).toBe(true)
  })

  describe('generation from identity skill affinities', () => {
    it.each([
      { case: 'skill 1 weighs 3', attributeType: ['CRIMSON'], expected: { CRIMSON: 3 } },
      {
        case: 'skill 2 weighs 2',
        attributeType: ['AZURE', 'CRIMSON'],
        expected: { AZURE: 3, CRIMSON: 2 },
      },
      {
        case: 'skill 3 weighs 1',
        attributeType: ['AZURE', 'AZURE', 'CRIMSON'],
        expected: { AZURE: 5, CRIMSON: 1 },
      },
      {
        case: 'skills past the third are ignored',
        attributeType: ['AZURE', 'AZURE', 'AZURE', 'CRIMSON'],
        expected: { AZURE: 6, CRIMSON: 0 },
      },
      {
        case: 'unknown affinity names are ignored',
        attributeType: ['NEUTRAL', 'CRIMSON'],
        expected: { CRIMSON: 2 },
      },
    ])('$case', ({ attributeType, expected }) => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A) }, [0]),
        { [IDENTITY_A]: { attributeType } },
        emptyEgoSpec,
      )

      expect(generatedOf(result)).toMatchObject(expected)
    })

    it('sums generation across every sinner in the deployment order', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A), '2': sinner(IDENTITY_B) }, [0, 1]),
        {
          [IDENTITY_A]: { attributeType: ['CRIMSON', 'CRIMSON', 'CRIMSON'] },
          [IDENTITY_B]: { attributeType: ['CRIMSON'] },
        },
        emptyEgoSpec,
      )

      expect(generatedOf(result).CRIMSON).toBe(9)
    })

    it('counts backup sinners beyond maxDeployed', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A), '2': sinner(IDENTITY_A) }, [0, 1], 1),
        { [IDENTITY_A]: { attributeType: ['CRIMSON'] } },
        emptyEgoSpec,
      )

      expect(generatedOf(result).CRIMSON).toBe(6)
    })

    it('ignores sinner codes with no equipment and identities with no spec', () => {
      const result = computeAffinityEA(
        deck({ '2': sinner(IDENTITY_UNKNOWN) }, [0, 1]),
        { [IDENTITY_A]: { attributeType: ['CRIMSON'] } },
        emptyEgoSpec,
      )

      expect(generatedOf(result).CRIMSON).toBe(0)
    })
  })

  describe('consumption from equipped EGO requirements', () => {
    it.each<{ case: string; requirements: Record<string, number>; expected: number }>([
      { case: 'a single requirement is consumed', requirements: { CRIMSON: 4 }, expected: 4 },
      { case: 'a zero cost is ignored', requirements: { CRIMSON: 0 }, expected: 0 },
      { case: 'a negative cost is ignored', requirements: { CRIMSON: -3 }, expected: 0 },
      {
        case: 'unknown affinity keys are ignored',
        requirements: { NEUTRAL: 5, CRIMSON: 2 },
        expected: 2,
      },
    ])('$case', ({ requirements, expected }) => {
      const result = computeAffinityEA(deck({ '1': sinner(IDENTITY_A, [EGO_A]) }, [0]), emptySpec, {
        [EGO_A]: { requirements },
      })

      expect(consumedOf(result).CRIMSON).toBe(expected)
    })

    it('sums requirements across every equipped EGO', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A, [EGO_A, EGO_B]) }, [0]),
        emptySpec,
        {
          [EGO_A]: { requirements: { CRIMSON: 3, AZURE: 2 } },
          [EGO_B]: { requirements: { CRIMSON: 1 } },
        },
      )

      expect(consumedOf(result)).toMatchObject({ CRIMSON: 4, AZURE: 2 })
    })

    it('ignores EGOs with no spec entry', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A, [EGO_MISSING]) }, [0]),
        emptySpec,
        {
          [EGO_A]: { requirements: { CRIMSON: 3 } },
        },
      )

      expect(consumedOf(result).CRIMSON).toBe(0)
    })

    it('reports consumption exceeding generation without clamping', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner(IDENTITY_A, [EGO_A]) }, [0]),
        { [IDENTITY_A]: { attributeType: ['CRIMSON'] } },
        { [EGO_A]: { requirements: { CRIMSON: 9 } } },
      )

      expect(result.find((entry) => entry.affinity === 'CRIMSON')).toEqual({
        affinity: 'CRIMSON',
        generated: 3,
        consumed: 9,
      })
    })
  })
})

describe('computeKeywordEA', () => {
  it('returns every status effect at zero for an empty deck', () => {
    const result = computeKeywordEA(deck({}, []), emptySpec, NO_GIFTS)

    expect(result.map((entry) => entry.keyword)).toEqual([...STATUS_EFFECTS])
    expect(
      result.every(({ count, deployedCount, allCount }) => count + deployedCount + allCount === 0),
    ).toBe(true)
  })

  it.each([
    {
      case: 'a deployed sinner counts in both scopes',
      maxDeployed: 2,
      expected: { deployedCount: 1, allCount: 1 },
    },
    {
      case: 'a backup sinner counts only in the full roster',
      maxDeployed: 1,
      expected: { deployedCount: 0, allCount: 1 },
    },
  ])('$case', ({ maxDeployed, expected }) => {
    const result = computeKeywordEA(
      deck({ '1': sinner(IDENTITY_A), '2': sinner(IDENTITY_B) }, [0, 1], maxDeployed),
      { [IDENTITY_A]: { skillKeywordList: [] }, [IDENTITY_B]: { skillKeywordList: ['Sinking'] } },
      NO_GIFTS,
    )
    const sinking = result.find((entry) => entry.keyword === 'Sinking')

    expect(sinking).toMatchObject(expected)
  })

  it('mirrors the full roster tally into count', () => {
    const result = computeKeywordEA(
      deck({ '1': sinner(IDENTITY_A), '2': sinner(IDENTITY_B) }, [0, 1], 1),
      {
        [IDENTITY_A]: { skillKeywordList: ['Burst'] },
        [IDENTITY_B]: { skillKeywordList: ['Burst'] },
      },
      NO_GIFTS,
    )

    expect(result.find((entry) => entry.keyword === 'Burst')).toEqual({
      keyword: 'Burst',
      count: 2,
      deployedCount: 1,
      allCount: 2,
    })
  })

  it('tallies a keyword once per entry in the identity skill keyword list', () => {
    const result = computeKeywordEA(
      deck({ '1': sinner(IDENTITY_A) }, [0]),
      { [IDENTITY_A]: { skillKeywordList: ['Charge', 'Charge', 'Charge'] } },
      NO_GIFTS,
    )

    expect(result.find((entry) => entry.keyword === 'Charge')).toMatchObject({
      deployedCount: 3,
      allCount: 3,
    })
  })

  it('ignores keywords outside the status effect list', () => {
    const result = computeKeywordEA(
      deck({ '1': sinner(IDENTITY_A) }, [0]),
      { [IDENTITY_A]: { skillKeywordList: ['Rupture', 'Tremor', 'Breath'] } },
      NO_GIFTS,
    )

    expect(result.map((entry) => entry.keyword)).toEqual([...STATUS_EFFECTS])
    expect(result.find((entry) => entry.keyword === 'Breath')?.allCount).toBe(1)
  })

  it('ignores sinner codes with no equipment and identities with no spec', () => {
    const result = computeKeywordEA(
      deck({ '2': sinner(IDENTITY_UNKNOWN) }, [0, 1]),
      { [IDENTITY_A]: { skillKeywordList: ['Burst'] } },
      NO_GIFTS,
    )

    expect(result.every(({ allCount }) => allCount === 0)).toBe(true)
  })

  describe('keyword grants', () => {
    const countOf = (result: ReturnType<typeof computeKeywordEA>, keyword: string) =>
      result.find((entry) => entry.keyword === keyword)?.allCount

    const sinner05 = (threadspin: ThreadspinTier): Record<string, SinnerEquipment> => ({
      '5': {
        identity: { id: IDENTITY_10508, uptie: 4, level: 45 },
        egos: { ZAYIN: { id: EGO_20509, threadspin } },
      },
    })

    it('grants the EGO keyword at threadspin 2 and appends it to the base list', () => {
      const result = computeKeywordEA(
        deck(sinner05(2), [4]),
        { [IDENTITY_10508]: { skillKeywordList: ['Breath'] } },
        NO_GIFTS,
      )

      expect(countOf(result, 'Laceration')).toBe(1)
      expect(countOf(result, 'Breath')).toBe(1)
    })

    it('withholds the EGO grant below threadspin 2', () => {
      const result = computeKeywordEA(
        deck(sinner05(1), [4]),
        { [IDENTITY_10508]: { skillKeywordList: ['Breath'] } },
        NO_GIFTS,
      )

      expect(countOf(result, 'Laceration')).toBe(0)
    })

    it('does not grant to a different identity of the same sinner', () => {
      const equipment: Record<string, SinnerEquipment> = {
        '5': {
          identity: { id: asIdentityId('10501'), uptie: 4, level: 45 },
          egos: { ZAYIN: { id: EGO_20509, threadspin: 4 } },
        },
      }
      const result = computeKeywordEA(deck(equipment, [4]), emptySpec, NO_GIFTS)

      expect(countOf(result, 'Laceration')).toBe(0)
    })

    it('grants the gift keyword to its identity while the gift is owned', () => {
      const equipment: Record<string, SinnerEquipment> = {
        '10': { identity: { id: IDENTITY_11009, uptie: 4, level: 45 }, egos: {} },
      }
      const result = computeKeywordEA(
        deck(equipment, [9]),
        { [IDENTITY_11009]: { skillKeywordList: ['Combustion'] } },
        new Set([GIFT_9282]),
      )

      expect(countOf(result, 'Vibration')).toBe(1)
      expect(countOf(result, 'Combustion')).toBe(1)
    })

    it('deduplicates a granted keyword the identity already applies', () => {
      const result = computeKeywordEA(
        deck(sinner05(4), [4]),
        { [IDENTITY_10508]: { skillKeywordList: ['Laceration'] } },
        NO_GIFTS,
      )

      expect(countOf(result, 'Laceration')).toBe(1)
    })
  })
})
