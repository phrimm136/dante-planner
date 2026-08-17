import { test, expect } from '@playwright/test'
import * as identity from '@/pages/identity/schemas/IdentitySchemas'
import * as panicInfo from '@/pages/identity/schemas/PanicInfoSchemas'
import * as ego from '@/pages/ego/schemas/EGOSchemas'
import * as egoGift from '@/pages/egoGift/schemas/EGOGiftSchemas'
import * as egoGiftObservation from '@/pages/egoGift/schemas/EGOGiftObservationSchemas'
import * as themePack from '@/pages/themePack/schemas/ThemePackSchemas'
import * as abEvent from '@/pages/abEvent/schemas/AbEventSchemas'
import * as planner from '@/pages/planner/schemas/PlannerSchemas'
import * as plannerList from '@/pages/planner/schemas/PlannerListSchemas'
import * as startGift from '@/pages/planner/schemas/StartGiftSchemas'
import * as keyword from '@/shared/gameText/schemas/KeywordSchemas'
import * as battleKeyword from '@/shared/gameText/schemas/BattleKeywordsSchemas'

// Vite tree-shakes a barrel's component graph away; Node evaluates the whole graph.
//
// Import these statically. Under dynamic import() Playwright routes through real ESM, where the
// JSON that shared/gameData/constants.ts reads needs an import attribute, and every module fails
// regardless of what it imports.
const schemaModules = {
  identity,
  panicInfo,
  ego,
  egoGift,
  egoGiftObservation,
  themePack,
  abEvent,
  planner,
  plannerList,
  startGift,
  keyword,
  battleKeyword,
}

for (const [name, module] of Object.entries(schemaModules)) {
  test(`${name} schemas resolve outside a bundler`, async () => {
    expect(Object.keys(module).length).toBeGreaterThan(0)
  })
}
