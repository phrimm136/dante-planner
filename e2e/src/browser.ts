import { test as base } from '@playwright/test'

/**
 * The browser-tier `test`, with the isolation the app's local-first storage needs.
 *
 * Playwright already hands every test its own `BrowserContext`, so cookies and localStorage start
 * empty. IndexedDB does not follow that rule for this app: `lib/storage.ts` opens a database named
 * `danteplanner` and the planner store is written by auto-save on every keystroke, so a spec that
 * edits a planner leaves a copy behind, and the next spec's editor loads that copy instead of the
 * one it seeded. Overriding the `context` fixture rather than adding a `beforeEach` puts the
 * deletion before the app's first script, which is the only point at which the database is
 * guaranteed to be closed.
 *
 * The marker keeps it to once per context: an init script runs on every document load, and a
 * spec that navigates after writing would otherwise lose what it just wrote.
 */
export const test = base.extend({
  context: async ({ context }, use) => {
    await context.addInitScript(() => {
      const MARKER = 'e2e-idb-cleared'
      if (sessionStorage.getItem(MARKER)) return
      sessionStorage.setItem(MARKER, '1')
      indexedDB.deleteDatabase('danteplanner')
    })
    await use(context)
  },
})

export { expect } from '@playwright/test'
