import { test as base, expect, devices, type Browser, type BrowserContext } from '@playwright/test'
import { DB_NAME, DB_VERSION, STORAGE_STORE_NAME } from '@/lib/storage'
import { readPlannerRows } from '../src/localPlanner'
import { armOnlyRendererKill, launchSingleRendererBrowser } from '../src/rendererKill'

/**
 * A mobile tab discard kills the renderer and leaves the browser process, which owns
 * IndexedDB, alive. The phone's low-memory killer does it with a SIGKILL, and a SIGKILL,
 * unlike a CDP `Page.crash` (a SIGTRAP), never reaches the host's core-dump handler, which
 * would delay the crash event by seconds under parallel load. Guest editing needs no
 * account. The assertion reads the store from a new tab, since a crashed one cannot be
 * navigated, and that is why this file takes Playwright's own `test` rather than the
 * browser fixture: the fixture wipes the database once per tab, which would empty the
 * store between the crash and the read. Each test's context starts empty anyway.
 */
const test = base.extend<{ singleRendererBrowser: Browser; mobileContext: BrowserContext }>({
  singleRendererBrowser: async ({}, use) => {
    const browser = await launchSingleRendererBrowser()
    try {
      await use(browser)
    } finally {
      await browser.close()
    }
  },
  mobileContext: async ({ singleRendererBrowser }, use, testInfo) => {
    await use(
      await singleRendererBrowser.newContext({
        ...devices['Pixel 7'],
        baseURL: testInfo.project.use.baseURL,
      }),
    )
  },
})

/** Delay before the crash; it must fall inside the control's 1.5 s busy task. */
const CRASH_INSIDE_BUSY_TASK_MS = 300

test('note text typed the instant before a renderer crash is in IndexedDB afterwards', async ({
  singleRendererBrowser,
  mobileContext,
}) => {
  const marker = `discard ${Date.now()}`

  const page = await mobileContext.newPage()
  await page.goto('/planner/md/new', { waitUntil: 'domcontentloaded' })
  const note = page.locator('.note-editor-content').first()
  await note.waitFor({ state: 'visible', timeout: 20_000 })
  await note.click()

  const killRenderer = await armOnlyRendererKill(singleRendererBrowser)
  await page.keyboard.insertText(marker)
  // No await between the keystroke and the crash: the write must already be committed.
  killRenderer()
  await page.waitForEvent('crash', { timeout: 5_000 })

  const fresh = await mobileContext.newPage()
  await fresh.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const rows = await readPlannerRows(fresh)

  expect(
    rows.some((row) => row.includes(marker)),
    'the keystroke died with the renderer',
  ).toBe(true)
})

test('a put committed inside a task survives a renderer crash in that same task', async ({
  singleRendererBrowser,
  mobileContext,
}) => {
  // The positive control for the case above: it proves the crash leaves IndexedDB intact
  // for a write that was committed, so a red run there is the app's fault, not the loop's.
  const key = `planner:control-${Date.now()}`

  const page = await mobileContext.newPage()
  await page.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const killRenderer = await armOnlyRendererKill(singleRendererBrowser)

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
  await page.waitForTimeout(CRASH_INSIDE_BUSY_TASK_MS)
  killRenderer()
  await page.waitForEvent('crash', { timeout: 5_000 })
  await busy

  const fresh = await mobileContext.newPage()
  await fresh.goto('/planner/md', { waitUntil: 'domcontentloaded' })
  const rows = await readPlannerRows(fresh)

  expect(rows.some((row) => row.includes('"control":true'))).toBe(true)
})
