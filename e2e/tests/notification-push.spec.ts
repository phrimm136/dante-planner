import { test, expect } from '../src/browser'
import { createAuthenticatedApi, authenticateContext } from '../src/auth'
import { producesRequest } from '../src/gestures'
import { dropPlanner, seedPublishedPlanner } from '../src/plannerFixture'
import { closeSeedPool, createUser, deleteUser } from '../src/seed'

// Server push to a client that did nothing: another user's action must reach an open browser
// through the SSE channel, with no navigation and no reload. ADR 072 makes the push a best-effort
// hint whose delivery floor is the client's refetch, so both specs assert the state the
// invalidation produces (a badge, a rendered comment) rather than the push's latency.
//
// The originating device is excluded from its own SSE events by design, so a second actor —
// commenter over the API, owner in the browser — is what makes the channel observable at all.

// The dispatcher hop is eager but async, and a lost push heals only at the relay interval, so
// the budget is the worst case ADR 072 promises rather than the happy path.
test.describe.configure({ timeout: 90_000 })

test.afterAll(closeSeedPool)

test('a comment on a published planner pushes a refetch to the owner and lands in their badge', async ({
  page,
  context,
  baseURL,
  browser,
}) => {
  const owner = await seedPublishedPlanner(baseURL!, 'push-badge')
  const commenter = await createUser('push-commenter')
  const commenterApi = await createAuthenticatedApi(baseURL!, commenter.id)

  // The negative half of targeted delivery: an unrelated user with their own open stream must
  // receive nothing for someone else's planner.
  const bystander = await createUser('push-bystander')
  const bystanderApi = await createAuthenticatedApi(baseURL!, bystander.id)
  const bystanderContext = await browser.newContext({ baseURL: baseURL! })
  const bystanderPage = await bystanderContext.newPage()

  try {
    const bystanderSettings = await bystanderApi.put('/api/user/settings', {
      data: { syncEnabled: true },
    })
    expect(bystanderSettings.status(), await bystanderSettings.text()).toBe(200)
    await authenticateContext(bystanderContext, bystander.id, baseURL!)
    const bystanderSse = bystanderPage.waitForResponse(
      (response) => response.url().includes('/api/sse/subscribe'),
      { timeout: 20_000 },
    )
    await bystanderPage.goto('/', { waitUntil: 'domcontentloaded' })
    await bystanderSse
    await authenticateContext(context, owner.user.id, baseURL!)
    // A push released before the emitter registers is delivered to nobody and never resent
    // (the outbox row is already stamped dispatched), so the comment must wait for the
    // stream, not merely for the page. The waiter starts before goto or it misses the
    // already-opened response.
    const sseReady = page.waitForResponse(
      (response) => response.url().includes('/api/sse/subscribe'),
      { timeout: 20_000 },
    )
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    await sseReady

    const badge = page.getByRole('banner').locator('span', { hasText: /^\d+$/ })
    await expect(badge, 'a fresh account already carried unread notifications').toHaveCount(0)

    // Transmission: nothing on an idle page requests the unread count, so the refetch this
    // waits for can only be the notify:comment push arriving and invalidating.
    await producesRequest(
      page,
      { method: 'GET', url: '/api/notifications/unread-count' },
      async () => {
        const commented = await commenterApi.post(`/api/planner/${owner.plannerId}/comments`, {
          data: { content: `<p>e2e push ${owner.plannerId.slice(0, 8)}</p>` },
        })
        expect(commented.status(), await commented.text()).toBe(201)
      },
      { timeout: 30_000 },
    )

    // The visible-tab push also shows the in-app toast, addressed by content: the owner's
    // planner title rides the body.
    await expect(page.getByText('New comment')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText(owner.title, { exact: false }).first()).toBeVisible()

    // Delivery is targeted: the bystander's open stream carried nothing. Asserted only after
    // the owner's toast proves the event fired at all.
    await bystanderPage.waitForTimeout(2_000)
    await expect(bystanderPage.getByText('New comment')).toHaveCount(0)
    await expect(
      bystanderPage.getByRole('banner').locator('span', { hasText: /^\d+$/ }),
    ).toHaveCount(0)

    // The push-driven refetch can race replication and read zero, and unread-count's staleTime
    // then blocks every focus heal for its whole window — so the badge is only asserted on a
    // fresh mount, where the row is read unconditionally. The push-arrives-then-badge-shows
    // property has no deterministic form until that stale-refetch gap is decided away.
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(badge).toHaveText('1', { timeout: 20_000 })
  } finally {
    await bystanderContext.close()
    await bystanderApi.dispose()
    await deleteUser(bystander)
    await commenterApi.dispose()
    await deleteUser(commenter)
    await dropPlanner(owner)
  }
})

test('a comment appears live in an open anonymous viewer', async ({ page, baseURL }) => {
  const owner = await seedPublishedPlanner(baseURL!, 'push-viewer')
  const commenter = await createUser('push-viewer-commenter')
  const commenterApi = await createAuthenticatedApi(baseURL!, commenter.id)
  const commentText = `e2e live comment ${owner.plannerId.slice(0, 8)}`

  try {
    const commentsStreamReady = page.waitForResponse(
      (response) => response.url().includes('/comments/events'),
      { timeout: 20_000 },
    )
    await page.goto(`/planner/md/gesellschaft/${owner.plannerId}`, {
      waitUntil: 'domcontentloaded',
    })
    await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
    await expect(page.getByText(owner.title)).toBeVisible()
    await commentsStreamReady

    const commented = await commenterApi.post(`/api/planner/${owner.plannerId}/comments`, {
      data: { content: `<p>${commentText}</p>` },
    })
    expect(commented.status(), await commented.text()).toBe(201)

    await expect(page.getByText(commentText)).toBeVisible({ timeout: 30_000 })
  } finally {
    await commenterApi.dispose()
    await deleteUser(commenter)
    await dropPlanner(owner)
  }
})
