/**
 * deckEA.test.ts
 *
 * Unit tests for the deck EA tallies: affinity generation weighting, EGO
 * consumption, and status effect keyword counts across roster scopes.
 */

import { describe, it, expect } from 'vitest'
import { AFFINITIES, STATUS_EFFECTS } from '@/shared/gameData'
import { computeAffinityEA, computeKeywordEA } from '../deckEA'
import type { EGOEASpec, IdentityEASpec } from '../deckEA'
import type { AffinityCount, DeckState, SinnerEquipment } from '../../types/DeckTypes'

const sinner = (identityId: string, egoIds: string[] = []): SinnerEquipment => ({
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
        deck({ '1': sinner('id-a') }, [0]),
        { 'id-a': { attributeType } },
        emptyEgoSpec,
      )

      expect(generatedOf(result)).toMatchObject(expected)
    })

    it('sums generation across every sinner in the deployment order', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner('id-a'), '2': sinner('id-b') }, [0, 1]),
        {
          'id-a': { attributeType: ['CRIMSON', 'CRIMSON', 'CRIMSON'] },
          'id-b': { attributeType: ['CRIMSON'] },
        },
        emptyEgoSpec,
      )

      expect(generatedOf(result).CRIMSON).toBe(9)
    })

    it('counts backup sinners beyond maxDeployed', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner('id-a'), '2': sinner('id-a') }, [0, 1], 1),
        { 'id-a': { attributeType: ['CRIMSON'] } },
        emptyEgoSpec,
      )

      expect(generatedOf(result).CRIMSON).toBe(6)
    })

    it('ignores sinner codes with no equipment and identities with no spec', () => {
      const result = computeAffinityEA(
        deck({ '2': sinner('unknown-id') }, [0, 1]),
        { 'id-a': { attributeType: ['CRIMSON'] } },
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
      const result = computeAffinityEA(deck({ '1': sinner('id-a', ['ego-a']) }, [0]), emptySpec, {
        'ego-a': { requirements },
      })

      expect(consumedOf(result).CRIMSON).toBe(expected)
    })

    it('sums requirements across every equipped EGO', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner('id-a', ['ego-a', 'ego-b']) }, [0]),
        emptySpec,
        {
          'ego-a': { requirements: { CRIMSON: 3, AZURE: 2 } },
          'ego-b': { requirements: { CRIMSON: 1 } },
        },
      )

      expect(consumedOf(result)).toMatchObject({ CRIMSON: 4, AZURE: 2 })
    })

    it('ignores EGOs with no spec entry', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner('id-a', ['ego-missing']) }, [0]),
        emptySpec,
        {
          'ego-a': { requirements: { CRIMSON: 3 } },
        },
      )

      expect(consumedOf(result).CRIMSON).toBe(0)
    })

    it('reports consumption exceeding generation without clamping', () => {
      const result = computeAffinityEA(
        deck({ '1': sinner('id-a', ['ego-a']) }, [0]),
        { 'id-a': { attributeType: ['CRIMSON'] } },
        { 'ego-a': { requirements: { CRIMSON: 9 } } },
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
    const result = computeKeywordEA(deck({}, []), emptySpec)

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
      deck({ '1': sinner('id-a'), '2': sinner('id-b') }, [0, 1], maxDeployed),
      { 'id-a': { skillKeywordList: [] }, 'id-b': { skillKeywordList: ['Sinking'] } },
    )
    const sinking = result.find((entry) => entry.keyword === 'Sinking')

    expect(sinking).toMatchObject(expected)
  })

  it('mirrors the full roster tally into count', () => {
    const result = computeKeywordEA(deck({ '1': sinner('id-a'), '2': sinner('id-b') }, [0, 1], 1), {
      'id-a': { skillKeywordList: ['Burst'] },
      'id-b': { skillKeywordList: ['Burst'] },
    })

    expect(result.find((entry) => entry.keyword === 'Burst')).toEqual({
      keyword: 'Burst',
      count: 2,
      deployedCount: 1,
      allCount: 2,
    })
  })

  it('tallies a keyword once per entry in the identity skill keyword list', () => {
    const result = computeKeywordEA(deck({ '1': sinner('id-a') }, [0]), {
      'id-a': { skillKeywordList: ['Charge', 'Charge', 'Charge'] },
    })

    expect(result.find((entry) => entry.keyword === 'Charge')).toMatchObject({
      deployedCount: 3,
      allCount: 3,
    })
  })

  it('ignores keywords outside the status effect list', () => {
    const result = computeKeywordEA(deck({ '1': sinner('id-a') }, [0]), {
      'id-a': { skillKeywordList: ['Rupture', 'Tremor', 'Breath'] },
    })

    expect(result.map((entry) => entry.keyword)).toEqual([...STATUS_EFFECTS])
    expect(result.find((entry) => entry.keyword === 'Breath')?.allCount).toBe(1)
  })

  it('ignores sinner codes with no equipment and identities with no spec', () => {
    const result = computeKeywordEA(deck({ '2': sinner('unknown-id') }, [0, 1]), {
      'id-a': { skillKeywordList: ['Burst'] },
    })

    expect(result.every(({ allCount }) => allCount === 0)).toBe(true)
  })
})
