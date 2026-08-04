/**
 * Facet Parity Support
 *
 * Enumerates filter-selection combinations and compares two predicates over a
 * fixture item set, used by the per-list facet parity suites.
 */

export type SelectionDomains<TState> = {
  [K in keyof TState]?: readonly (readonly unknown[])[]
}

export interface ParityMismatch<TState> {
  state: TState
  expected: string[]
  actual: string[]
}

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b)
}

/**
 * Builds the cartesian product of the per-facet selection domains on top of
 * `base`. Above `cap` combinations the product is sampled with a stride coprime
 * to its size, and every single-facet selection is appended so each facet keeps
 * full coverage.
 */
export function enumerateSelectionStates<TState extends object>(
  base: TState,
  domains: SelectionDomains<TState>,
  cap = 20000,
): TState[] {
  const keys = Object.keys(domains) as (keyof TState)[]
  const optionLists = keys.map((key) => domains[key] ?? [])
  const total = optionLists.reduce((product, options) => product * options.length, 1)

  const stateAt = (index: number): TState => {
    const state = { ...base }
    let rest = index
    keys.forEach((key, position) => {
      const options = optionLists[position]
      state[key] = new Set(options[rest % options.length]) as unknown as TState[typeof key]
      rest = Math.floor(rest / options.length)
    })
    return state
  }

  let stride = total > cap ? Math.ceil(total / cap) : 1
  while (stride > 1 && gcd(stride, total) !== 1) stride += 1

  const states: TState[] = []
  const count = Math.ceil(total / stride)
  for (let step = 0; step < count; step += 1) {
    states.push(stateAt((step * stride) % total))
  }

  if (stride > 1) {
    keys.forEach((key, position) => {
      for (const options of optionLists[position]) {
        states.push({ ...base, [key]: new Set(options) })
      }
    })
  }

  return states
}

/** Collects every state where the two predicates disagree on the surviving ids. */
export function findParityMismatches<TItem extends { id: string }, TState>(
  items: readonly TItem[],
  states: readonly TState[],
  expected: (item: TItem, state: TState) => boolean,
  actual: (item: TItem, state: TState) => boolean,
): ParityMismatch<TState>[] {
  const mismatches: ParityMismatch<TState>[] = []

  for (const state of states) {
    const expectedIds = items.filter((item) => expected(item, state)).map((item) => item.id)
    const actualIds = items.filter((item) => actual(item, state)).map((item) => item.id)
    if (expectedIds.join('|') !== actualIds.join('|')) {
      mismatches.push({ state, expected: expectedIds, actual: actualIds })
    }
  }

  return mismatches
}
