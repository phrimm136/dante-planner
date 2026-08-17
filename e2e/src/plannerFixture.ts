import { expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import type { APIRequestContext } from '@playwright/test'
import { createAuthenticatedApi } from './auth'
import { browserPlannerPayload } from './plannerContent'
import { createUser, deleteUser, type SeededUser } from './seed'

/**
 * An account and a planner that belong to one spec and nothing else.
 *
 * The environment's database is shared, so a browser spec cannot assert on a listing's length or
 * its first row (docs/testing-principles.md §13). It asserts on `title`, which is unique to the
 * run that made it.
 */
export interface PlannerFixture {
  user: SeededUser
  api: APIRequestContext
  plannerId: string
  title: string
}

export async function seedPlanner(
  baseURL: string,
  label: string,
  syncEnabled = true,
): Promise<PlannerFixture> {
  const user = await createUser(label)
  const api = await createAuthenticatedApi(baseURL, user.id)
  const plannerId = randomUUID()
  const title = `e2e ${label} ${plannerId.slice(0, 8)}`

  // A fresh account's syncEnabled is null, and GlobalLayout answers that with a modal that
  // intercepts every pointer event on the page. Choosing for it is a precondition of any browser
  // spec, not a detail of the ones that care about sync — and manual save reaches the network
  // only when it is on.
  const settings = await api.put('/api/user/settings', { data: { syncEnabled } })
  expect(settings.status(), await settings.text()).toBe(200)

  const created = await api.put(`/api/planner/md/${plannerId}`, {
    data: browserPlannerPayload(plannerId, title),
  })
  expect([200, 201], await created.text()).toContain(created.status())

  return { user, api, plannerId, title }
}

export async function publish(fixture: PlannerFixture, published = true): Promise<void> {
  const intent = published ? 'publish' : 'unpublish'
  const response = await fixture.api.post(`/api/planner/md/${fixture.plannerId}/${intent}`)
  expect(response.status(), await response.text()).toBe(200)
}

export async function seedPublishedPlanner(
  baseURL: string,
  label: string,
): Promise<PlannerFixture> {
  const fixture = await seedPlanner(baseURL, label)
  await publish(fixture)
  return fixture
}

/** Posts a top-level comment as the fixture's own user, so the browser sees it as the author. */
export async function seedComment(fixture: PlannerFixture, content: string): Promise<string> {
  const response = await fixture.api.post(`/api/planner/${fixture.plannerId}/comments`, {
    data: { content },
  })
  expect(response.status(), await response.text()).toBe(201)
  return String((await response.json()).id)
}

/**
 * Deletes through the API rather than the database: the projection tables carry no foreign key to
 * the planner, so dropping the user row alone leaves planner_content, planner_publication and the
 * rest behind for the next run's listings to return.
 */
export async function dropPlanner(fixture: PlannerFixture): Promise<void> {
  await fixture.api.delete(`/api/planner/md/${fixture.plannerId}`)
  await fixture.api.dispose()
  await deleteUser(fixture.user)
}
