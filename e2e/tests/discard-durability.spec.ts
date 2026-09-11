import { test, expect, devices } from '@playwright/test'
import { DB_NAME, DB_VERSION, STORAGE_STORE_NAME } from '@/lib/storage'
import { readPlannerRows } from '../src/localPlanner'

/**
 * A mobile tab discard kills the renderer and leaves the browser process, which owns
 * IndexedDB, alive. `Page.crash` over CDP is that exact shape, so the discard the phone
 * performs under memory pressure runs here deterministically. Guest editing needs no
 * account. The assertion reads the store from a new tab, since a crashed one cannot be
 * navigated, and that is why this file takes Playwright's own `test` rather than the
 * browser fixture: the fixture wipes the database once per tab, which would empty the
 * store between the crash and the read. Each test's context starts empty anyway.
 */

test.use({ ...devices['Pixel 7'] })

test('note text typed the instant before a renderer crash is in IndexedDB afterwards', async ({
  page,
  context,
}) => {
  const marker = `discard ${Date.now()}`

  await page.goto('/planner/md/new', { waitUntil: 'domcontentloaded' })
  const note = page.locator('.note-editor-content').first()
  await note.waitFor({ state: 'visible', timeout: 20_000 })
  await note.click()

  const cdp = await context.newCDPSession(page)
  await page.keyboard.insertText(marker)
  // No await between the keystroke and the crash: the write must already be committed.
  void cdp.send('Page.crash').catch(() => undefined)
  await page.waitForEvent('crash', { timeout: 5_000 })

  const fresh = await context.newPage()
  await fresh.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const rows = await readPlannerRows(fresh)

  expect(rows.some((row) => row.includes(marker)), 'the keystroke died with the renderer').toBe(
    true,
  )
})

test('a put committed inside a task survives a renderer crash in that same task', async ({
  page,
  context,
}) => {
  // The positive control for the case above: it proves the crash leaves IndexedDB intact
  // for a write that was committed, so a red run there is the app's fault, not the loop's.
  const key = `planner:control-${Date.now()}`

  await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const cdp = await context.newCDPSession(page)

  const busy = page
    .evaluate(
      ([db, version, store, rowKey]) =>
        new Promise<void>((resolve, reject) => {
          const request = indexedDB.open(db, version)
          request.onupgradeneeded = () => {
            if (!request.result.objectStoreNames.contains(store)) {
              request.result.createObjectStore(store)
            }
          }
          request.onerror = () => reject(request.error)
          request.onblocked = () => reject(new Error('blocked'))
          request.onsuccess = () => {
            const transaction = request.result.transaction(store, 'readwrite')
            transaction.objectStore(store).put(JSON.stringify({ control: true }), rowKey)
            transaction.commit()
            const until = performance.now() + 1_500
            while (performance.now() < until) {
              /* hold the task so the crash lands before it ends */
            }
            resolve()
          }
        }),
      [DB_NAME, DB_VERSION, STORAGE_STORE_NAME, key] as const,
    )
    .catch(() => undefined)
  await page.waitForTimeout(300)
  void cdp.send('Page.crash').catch(() => undefined)
  await page.waitForEvent('crash', { timeout: 5_000 })
  await busy

  const fresh = await context.newPage()
  await fresh.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const rows = await readPlannerRows(fresh)

  expect(rows.some((row) => row.includes('"control":true'))).toBe(true)
})
