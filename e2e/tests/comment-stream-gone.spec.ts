import { test, expect } from '../src/browser'
import { COMMENT_SSE_CONNECTION } from '@/lib/constants'
import { becomesTrue } from '../src/consistency'
import { dropPlanner, seedPublishedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'

// The comment stream's path names one planner, so a 404 on it is terminal: the planner is gone and
// no reconnect can bring it back. The incident shape is a stream that was already open when the
// planner went away — nothing on the server closes it, so the 404 is only reachable once the
// connection drops and the engine reconnects. Offline/online is what forces that drop.

/** The copy `planner:sync.removedOnAnotherDevice` renders in the suite's language. */
const REMOVED_COPY = 'This planner was removed on another device'

/** Long enough offline for the open stream to die; attempts made offline earn no response. */
const OFFLINE_DWELL_MS = 1_500

/** Longest a reconnect could wait before it would show up as a further response. */
const RECONNECT_WINDOW_MS = COMMENT_SSE_CONNECTION.MAX_DELAY + COMMENT_SSE_CONNECTION.MAX_JITTER

/** Budget for the post-drop reconnect, which backs off and carries up to 5s of jitter. */
const STREAM_GONE_DEADLINE_MS = 30_000

test.describe.configure({ timeout: 120_000 })

test.afterAll(closeSeedPool)

test('a stream whose planner was deleted reports gone once and stops reconnecting', async ({
  page,
  context,
  baseURL,
}) => {
  const owner = await seedPublishedPlanner(baseURL!, 'stream-gone')
  const eventsUrl = `/api/planner/${owner.plannerId}/comments/events`
  const streamStatuses: number[] = []
  page.on('response', (response) => {
    if (response.url().includes(eventsUrl)) streamStatuses.push(response.status())
  })

  try {
    await page.goto(`/planner/md/gesellschaft/${owner.plannerId}`, {
      waitUntil: 'domcontentloaded',
    })
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    await expect(page.getByText(owner.title)).toBeVisible()
    await becomesTrue(async () => streamStatuses.includes(200), {
      deadlineMs: 20_000,
      what: 'the comment stream opening against the published planner',
    })

    const openedResponses = streamStatuses.length
    const deleted = await owner.api.delete(`/api/planner/md/${owner.plannerId}`)
    expect(deleted.status(), await deleted.text()).toBeLessThan(300)

    // The delete leaves the open stream untouched, so the client has learned nothing yet.
    expect(streamStatuses).toHaveLength(openedResponses)

    await context.setOffline(true)
    await page.waitForTimeout(OFFLINE_DWELL_MS)
    await context.setOffline(false)

    await becomesTrue(async () => streamStatuses.includes(404), {
      deadlineMs: STREAM_GONE_DEADLINE_MS,
      what: 'the reconnect reading the planner as gone',
    })
    await expect(page.getByText(REMOVED_COPY)).toBeVisible({ timeout: 20_000 })

    const goneAt = streamStatuses.indexOf(404)
    await page.waitForTimeout(RECONNECT_WINDOW_MS)
    expect(streamStatuses.filter((status) => status === 404)).toHaveLength(1)
    expect(streamStatuses.slice(goneAt + 1)).toEqual([])
  } finally {
    await dropPlanner(owner)
  }
})
