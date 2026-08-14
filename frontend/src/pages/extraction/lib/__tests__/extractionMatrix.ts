/**
 * Input matrix for the extraction calculator characterization tests.
 *
 * `runMatrix` drives every exported calculator entry point over the same
 * deterministic case list and flattens each result into scalars, so two
 * implementations (or an implementation and a golden fixture) can be compared
 * field by field.
 *
 * Scales:
 * - `core`: the matrix pinned by the committed golden fixture
 * - `full`: the wider sweep, used when diffing two implementations directly
 */

import type { ExtractionTarget, ExtractionTargetType } from '../../types/ExtractionTypes'

export type CalculatorModule = typeof import('../extractionCalculator')

export type MatrixScalar = number | string | boolean

export interface MatrixCase {
  label: string
  values: MatrixScalar[]
}

export type MatrixGroups = Record<string, MatrixCase[]>

export type MatrixScale = 'core' | 'full'

const TYPES: ExtractionTargetType[] = ['threeStarId', 'ego', 'announcer']
const FLAGS = [false, true]

const HELPER_PULLS: Record<MatrixScale, number[]> = {
  core: [0, 1, 48, 100, 200, 201, 400, 1000],
  full: [-1, 0, 1, 2, 5, 10, 47, 48, 100, 130, 199, 200, 201, 250, 399, 400, 600, 1000, 2000],
}

const RATES: Record<MatrixScale, number[]> = {
  core: [0, 0.0065, 0.0145, 0.5, 1],
  full: [-0.1, 0, 0.0001, 0.0065, 0.013, 0.0145, 0.5, 0.999, 1, 1.5],
}

const FEATURED: Record<MatrixScale, number[]> = {
  core: [0, 1, 2, 3, 5],
  full: [-1, 0, 1, 2, 3, 4, 5, 10, 20],
}

const WANTED: Record<MatrixScale, number[]> = {
  core: [0, 1, 2, 4],
  full: [-1, 0, 1, 2, 3, 4, 6],
}

const EXTRACTION_PULLS: Record<MatrixScale, number[]> = {
  core: [0, 1, 100, 200, 201],
  full: [0, 1, 10, 48, 100, 199, 200, 201, 399, 400, 600, 1000],
}

const EXTRACTION_PITY: Record<MatrixScale, number[]> = {
  core: [0, 199],
  full: [0, 1, 50, 99, 100, 150, 199],
}

function target(
  type: ExtractionTargetType,
  wantedCopies: number,
  currentCopies: number,
): ExtractionTarget {
  return { type, wantedCopies, currentCopies }
}

interface TargetSet {
  label: string
  targets: ExtractionTarget[]
  core: boolean
}

const TARGET_SETS: TargetSet[] = [
  { label: 'empty', targets: [], core: true },
  { label: 'id1', targets: [target('threeStarId', 1, 0)], core: true },
  { label: 'id2', targets: [target('threeStarId', 2, 0)], core: true },
  { label: 'id3have1', targets: [target('threeStarId', 3, 1)], core: false },
  { label: 'id5', targets: [target('threeStarId', 5, 0)], core: false },
  { label: 'ego1', targets: [target('ego', 1, 0)], core: true },
  { label: 'ego4', targets: [target('ego', 4, 0)], core: true },
  { label: 'ann1', targets: [target('announcer', 1, 0)], core: true },
  { label: 'id1ego1', targets: [target('threeStarId', 1, 0), target('ego', 1, 0)], core: false },
  {
    label: 'id2ego2have1ann1',
    targets: [target('threeStarId', 2, 0), target('ego', 2, 1), target('announcer', 1, 0)],
    core: true,
  },
  { label: 'satisfied', targets: [target('threeStarId', 1, 1)], core: true },
  { label: 'oversatisfied', targets: [target('ego', 2, 3)], core: false },
]

interface FeaturedSet {
  label: string
  counts: Record<ExtractionTargetType, number>
  core: boolean
}

const FEATURED_SETS: FeaturedSet[] = [
  { label: 'f111', counts: { threeStarId: 1, ego: 1, announcer: 1 }, core: true },
  { label: 'f231', counts: { threeStarId: 2, ego: 3, announcer: 1 }, core: true },
  { label: 'f300', counts: { threeStarId: 3, ego: 0, announcer: 0 }, core: false },
  { label: 'f021', counts: { threeStarId: 0, ego: 2, announcer: 1 }, core: false },
  { label: 'f552', counts: { threeStarId: 5, ego: 5, announcer: 2 }, core: false },
  { label: 'f000', counts: { threeStarId: 0, ego: 0, announcer: 0 }, core: false },
]

function pick<T extends { core: boolean }>(sets: T[], scale: MatrixScale): T[] {
  return scale === 'full' ? sets : sets.filter((s) => s.core)
}

/**
 * Drive every calculator entry point over the matrix.
 *
 * @param mod - Calculator module under test
 * @param scale - Matrix width
 * @returns Flattened results grouped by entry point
 */
export function runMatrix(mod: CalculatorModule, scale: MatrixScale): MatrixGroups {
  const pulls = HELPER_PULLS[scale]
  const rates = RATES[scale]
  const featured = FEATURED[scale]
  const wanted = WANTED[scale]
  const targetSets = pick(TARGET_SETS, scale)
  const featuredSets = pick(FEATURED_SETS, scale)

  const groups: MatrixGroups = {
    rateTable: [],
    rateForTarget: [],
    singleTarget: [],
    atLeastKHits: [],
    couponCollector: [],
    naturalProbability: [],
    categoryDistribution: [],
    convolve: [],
    expectedPulls: [],
    lunacyCost: [],
    effectiveRates: [],
    multiTarget: [],
    pityAdjusted: [],
    successive: [],
    extraction: [],
  }

  for (const allEgoCollected of FLAGS) {
    for (const hasAnnouncer of FLAGS) {
      const table = mod.getEffectiveRates(allEgoCollected, hasAnnouncer)
      groups.rateTable.push({
        label: `allEgo=${allEgoCollected},ann=${hasAnnouncer}`,
        values: [
          mod.getRateTableName(allEgoCollected, hasAnnouncer),
          table.THREE_STAR_ID,
          table.TWO_STAR_ID,
          table.ONE_STAR_ID,
          table.EGO,
          table.ANNOUNCER,
        ],
      })
    }
  }

  for (const type of TYPES) {
    for (const count of featured) {
      for (const allEgoCollected of FLAGS) {
        groups.rateForTarget.push({
          label: `${type},featured=${count},allEgo=${allEgoCollected}`,
          values: [mod.calculateRateForTarget(type, count, allEgoCollected)],
        })
      }
    }
  }

  for (const p of pulls) {
    for (const rate of rates) {
      groups.singleTarget.push({
        label: `pulls=${p},rate=${rate}`,
        values: [mod.calculateSingleTargetProbability(p, rate)],
      })

      for (const hits of wanted) {
        groups.atLeastKHits.push({
          label: `pulls=${p},rate=${rate},hits=${hits}`,
          values: [mod.calculateAtLeastKHits(p, rate, hits)],
        })
      }

      for (const count of featured) {
        for (const want of wanted) {
          groups.couponCollector.push({
            label: `pulls=${p},rate=${rate},featured=${count},want=${want}`,
            values: [mod.calculateCouponCollectorProbability(p, rate, count, want)],
          })

          for (const type of TYPES) {
            groups.naturalProbability.push({
              label: `${type},pulls=${p},rate=${rate},featured=${count},want=${want}`,
              values: [mod.calculateNaturalProbability(type, rate, count, want, p)],
            })
          }
        }
      }
    }

    for (const type of TYPES) {
      for (const want of wanted) {
        for (const count of featured) {
          for (const allEgoCollected of FLAGS) {
            groups.categoryDistribution.push({
              label: `${type},pulls=${p},want=${want},featured=${count},allEgo=${allEgoCollected}`,
              values: mod.calculateCategoryDistribution(p, type, want, count, allEgoCollected),
            })
          }
        }
      }
    }

    groups.lunacyCost.push({ label: `pulls=${p}`, values: [mod.calculateLunacyCost(p)] })
  }

  for (const rate of rates) {
    groups.expectedPulls.push({ label: `rate=${rate}`, values: [mod.calculateExpectedPulls(rate)] })
  }

  groups.convolve.push(
    { label: 'flat', values: mod.convolveDistributions([0.5, 0.5], [0.25, 0.5, 0.25]) },
    { label: 'single', values: mod.convolveDistributions([1], [0.2, 0.3, 0.5]) },
    { label: 'zeros', values: mod.convolveDistributions([0, 0, 1], [0, 1]) },
    {
      label: 'ragged',
      values: mod.convolveDistributions([0.1, 0.2, 0.7], [0.4, 0.35, 0.2, 0.05]),
    },
  )

  for (const allEgoCollected of FLAGS) {
    for (const hasAnnouncer of FLAGS) {
      for (const threeStar of featured) {
        for (const ego of featured) {
          const rates_ = mod.calculateEffectiveRates(
            { allEgoCollected, hasAnnouncer },
            threeStar,
            ego,
          )
          groups.effectiveRates.push({
            label: `allEgo=${allEgoCollected},ann=${hasAnnouncer},id=${threeStar},ego=${ego}`,
            values: [
              rates_.threeStarIdEach,
              rates_.egoEach,
              rates_.announcer,
              rates_.threeStarIdTotal,
              rates_.egoTotal,
            ],
          })
        }
      }
    }
  }

  for (const set of targetSets) {
    for (const featuredSet of featuredSets) {
      for (const p of pulls) {
        for (const allEgoCollected of FLAGS) {
          const base = `${set.label},${featuredSet.label},pulls=${p},allEgo=${allEgoCollected}`

          groups.multiTarget.push({
            label: base,
            values: [
              mod.calculateMultiTargetProbability(
                set.targets,
                p,
                featuredSet.counts,
                allEgoCollected,
              ),
            ],
          })

          for (const pity of EXTRACTION_PITY[scale]) {
            const adjusted = mod.calculatePityAdjustedProbability(
              set.targets,
              p,
              featuredSet.counts,
              pity,
              allEgoCollected,
            )
            groups.pityAdjusted.push({
              label: `${base},pity=${pity}`,
              values: [adjusted.probability, adjusted.pityApplies],
            })
          }

          for (const pityCount of [0, 1, 2, 5]) {
            const successive = mod.calculateSuccessiveTargetProbabilities(
              set.targets,
              p,
              featuredSet.counts,
              allEgoCollected,
              pityCount,
            )
            groups.successive.push({
              label: `${base},pityCount=${pityCount}`,
              values: successive.flatMap((s) => [s.count, s.probability]),
            })
          }
        }
      }
    }
  }

  for (const set of targetSets) {
    for (const featuredSet of featuredSets) {
      for (const allEgoCollected of FLAGS) {
        for (const hasAnnouncer of FLAGS) {
          for (const plannedPulls of EXTRACTION_PULLS[scale]) {
            for (const currentPity of EXTRACTION_PITY[scale]) {
              const result = mod.calculateExtraction({
                plannedPulls,
                featuredThreeStarCount: featuredSet.counts.threeStarId,
                featuredEgoCount: featuredSet.counts.ego,
                featuredAnnouncerCount: featuredSet.counts.announcer,
                modifiers: { allEgoCollected, hasAnnouncer },
                targets: set.targets,
                currentPity,
              })

              groups.extraction.push({
                label: `${set.label},${featuredSet.label},allEgo=${allEgoCollected},ann=${hasAnnouncer},pulls=${plannedPulls},pity=${currentPity}`,
                values: [
                  result.anyTargetProbability,
                  result.allTargetProbability,
                  result.totalItemsWanted,
                  result.pityCount,
                  result.lunacyCost,
                  result.pullsUntilPity,
                  result.activeRateTable,
                  ...result.targetResults.flatMap((r) => [
                    r.probability,
                    r.expectedPulls,
                    r.pityApplies,
                  ]),
                  ...result.successiveProbabilities.flatMap((s) => [s.count, s.probability]),
                ],
              })
            }
          }
        }
      }
    }
  }

  return groups
}

/**
 * Encode a matrix scalar for JSON storage.
 *
 * JSON has no literal for the non-finite doubles or for negative zero, all of
 * which the calculator can return, so they round-trip as tagged strings.
 */
export function encodeScalar(value: MatrixScalar): MatrixScalar {
  if (typeof value !== 'number') {
    return value
  }
  if (Number.isFinite(value)) {
    return Object.is(value, -0) ? '-0' : value
  }
  return String(value)
}

/** Total number of scalar comparisons a matrix produces */
export function countScalars(groups: MatrixGroups): number {
  return Object.values(groups).reduce(
    (sum, cases) => sum + cases.reduce((n, c) => n + c.values.length, 0),
    0,
  )
}
