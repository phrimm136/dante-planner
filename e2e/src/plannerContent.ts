import identitySpecList from '@static/data/identitySpecList.json'
import egoSpecList from '@static/data/egoSpecList.json'
import themePackList from '@static/data/themePackList.json'
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

function minimalEquipment(): Record<string, unknown> {
  return Object.fromEntries(
    Object.keys(identityBySinner).map((sinner) => [
      sinner,
      {
        identity: { id: identityBySinner[sinner], uptie: 1, level: 1 },
        egos: { ZAYIN: { id: zayinBySinner[sinner], threadspin: 1 } },
      },
    ]),
  )
}

// The client re-validates before publishing, against the copy in IndexedDB rather than against the
// editor store, so the browser fixture has to satisfy rules the server payload never sees: every
// sinner's three skill slots summing to six, and one distinct theme pack per floor.
const SKILL_EA_BY_SLOT = { '0': 3, '1': 2, '2': 1 }
const FLOOR_COUNT = 15
const DIFFICULTY_NORMAL = 0

/**
 * The same equipment as `minimalPlannerContent`, unserialized and completed, for the copy the
 * browser keeps in IndexedDB.
 *
 * Publishable as it stands: the editor rejects a publish whose floors share a theme pack or lack
 * one, and it never reaches the network when it does.
 */
export function localPlannerContent(): Record<string, unknown> {
  const sinners = Object.keys(identityBySinner)
  const themePackIds = Object.keys(themePackList).sort().slice(0, FLOOR_COUNT)

  if (themePackIds.length < FLOOR_COUNT) {
    throw new Error(`themePackList carries ${themePackIds.length} packs, fewer than one per floor`)
  }

  return {
    selectedKeywords: [],
    selectedBuffIds: [],
    selectedGiftKeyword: null,
    selectedGiftIds: [],
    observationGiftIds: [],
    comprehensiveGiftIds: [],
    equipment: minimalEquipment(),
    deploymentOrder: [],
    skillEAState: Object.fromEntries(sinners.map((sinner) => [sinner, { ...SKILL_EA_BY_SLOT }])),
    floorSelections: themePackIds.map((themePackId) => ({
      themePackId,
      difficulty: DIFFICULTY_NORMAL,
      giftIds: [],
    })),
    sectionNotes: {},
  }
}

export function minimalPlannerContent(): string {
  const equipment = minimalEquipment()

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

/**
 * The write payload a browser spec seeds with.
 *
 * `plannerPayload` carries the least content the server will accept, which is enough for a listing
 * but not for the viewer: the detail page reads per-floor theme packs and gift selections, and
 * renders nothing when they are absent.
 */
export function browserPlannerPayload(id: string, title: string) {
  return { ...plannerPayload(id, title), content: JSON.stringify(localPlannerContent()) }
}

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
