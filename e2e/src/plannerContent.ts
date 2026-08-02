import identitySpecList from '@static/data/identitySpecList.json'
import egoSpecList from '@static/data/egoSpecList.json'
import { PLANNER_CONFIG } from '@/lib/constants'

// The write path validates every id against the shipped game data and requires all twelve sinners
// to carry an identity and a ZAYIN EGO. Ids encode their sinner in characters 1..3, so "10101" and
// "20101" both belong to sinner 01.
const SINNER_SLICE: [number, number] = [1, 3]

function bySinner(ids: string[]): Record<string, string> {
  const picked: Record<string, string> = {}
  for (const id of [...ids].sort()) {
    const sinner = id.slice(...SINNER_SLICE)
    picked[sinner] ??= id
  }
  return picked
}

const identityBySinner = bySinner(Object.keys(identitySpecList))
const zayinBySinner = bySinner(
  Object.entries(egoSpecList as Record<string, { egoType?: string }>)
    .filter(([, spec]) => spec.egoType === 'ZAYIN')
    .map(([id]) => id),
)

export function minimalPlannerContent(): string {
  const equipment = Object.fromEntries(
    Object.keys(identityBySinner).map((sinner) => [
      sinner,
      {
        identity: { id: identityBySinner[sinner], uptie: 1, level: 1 },
        egos: { ZAYIN: { id: zayinBySinner[sinner], threadspin: 1 } },
      },
    ]),
  )

  return JSON.stringify({
    selectedKeywords: [],
    equipment,
    deploymentOrder: [],
    floorSelections: [],
    sectionNotes: {},
  })
}

// The keyword reaches planner_keyword_filter through the rebuild procedure (migration V053), so a
// suite asserting the filter index picks its planners by this value.
export const PLANNER_KEYWORD = 'Combustion'

export function plannerPayload(id: string, title: string) {
  return {
    id,
    category: '5F',
    title,
    status: 'saved',
    content: minimalPlannerContent(),
    contentVersion: PLANNER_CONFIG.mdCurrentVersion,
    plannerType: 'MIRROR_DUNGEON',
    selectedKeywords: [PLANNER_KEYWORD],
  }
}
