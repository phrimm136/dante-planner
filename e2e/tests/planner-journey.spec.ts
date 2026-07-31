import { test, expect, request as playwrightRequest } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { authenticateContext, createAuthenticatedApi } from '../src/auth'
import { createUser, deleteUser } from '../src/seed'
import { PLANNER_CONFIG } from '@/lib/constants'
import { minimalPlannerContent } from '../src/plannerContent'

// The owner write path, end to end: a planner is created, published, and then read back through
// the anonymous list the community page uses. That last hop is the one no other tier covers,
// because it crosses the write aggregate, the catalog projection, and the client's own schema.


function plannerPayload(id: string, title: string) {
  return {
    id,
    category: '5F',
    title,
    status: 'saved',
    content: minimalPlannerContent(),
    contentVersion: PLANNER_CONFIG.mdCurrentVersion,
    plannerType: 'MIRROR_DUNGEON',
    selectedKeywords: ['Burn'],
  }
}

test('an owner publishes a planner and it reaches the community list', async ({ baseURL }) => {
  const user = createUser('list')
  const api = await createAuthenticatedApi(baseURL!, user.id)
  const plannerId = randomUUID()
  const title = `e2e list ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: plannerPayload(plannerId, title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    // Anonymous, because the community list is what a visitor sees.
    const anonymous = await playwrightRequest.newContext({ baseURL })
    const list = await anonymous.get('/api/planner/md/published?page=0&size=50')
    expect(list.status()).toBe(200)

    const titles = ((await list.json()).content as Array<{ title: string }>).map((p) => p.title)
    expect(titles).toContain(title)
    await anonymous.dispose()
  } finally {
    // Delete through the API, not the database. Deleting the user row alone leaves the FK-less
    // projections behind, because the application sweep is what removes them and the foreign-key
    // cascade only reaches the tables that declare one.
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    deleteUser(user)
  }
})

test('a published planner is visible on the community page', async ({ page, context, baseURL }) => {
  const user = createUser('page')
  const api = await createAuthenticatedApi(baseURL!, user.id)
  const plannerId = randomUUID()
  const title = `e2e page ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: plannerPayload(plannerId, title),
    })
    expect([200, 201], await created.text()).toContain(created.status())

    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    await authenticateContext(context, user.id, baseURL!)
    await page.goto('/planner/md/gesellschaft', { waitUntil: 'networkidle' })
    await expect(page.getByText(title)).toBeVisible()
  } finally {
    // Delete through the API, not the database. Deleting the user row alone leaves the FK-less
    // projections behind, because the application sweep is what removes them and the foreign-key
    // cascade only reaches the tables that declare one.
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    deleteUser(user)
  }
})
