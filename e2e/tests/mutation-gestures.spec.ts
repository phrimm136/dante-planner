import { test, expect } from '../src/browser'
import type { Page } from '@playwright/test'
import { authenticateContext, createAuthenticatedApi } from '../src/auth'
import { producesRequest, settled } from '../src/gestures'
import { seedLocalPlanner } from '../src/localPlanner'
import {
  dropPlanner,
  publish,
  seedComment,
  seedPlanner,
  seedPublishedPlanner,
} from '../src/plannerFixture'
import { closeSeedPool, createUser, deleteUser } from '../src/seed'

/**
 * One gesture per mutation the app offers, each asserting that the click reached the server.
 *
 * The failure these exist for is the silent one: a handler that never ran leaves no error to
 * assert the absence of, so a spec that only checks the resulting state passes on a stale read
 * and fails much later. `producesRequest` names the request the gesture exists to cause.
 *
 * Saving a section note also has its own spec, pinning the gesture defect that the button below
 * the editor still carries. This file drives the button above it, which the collapse cannot move.
 */

test.afterAll(closeSeedPool)

/**
 * Loads a route and waits for React to replace the crawler skeleton. An SSE stream rules out
 * waiting on the network, and the skeleton going is only the shell — sections below it arrive in
 * their own lazy chunks, so every caller still waits on the element it is about to drive.
 */
async function mounted(page: Page, route: string): Promise<void> {
  await page.goto(route, { waitUntil: 'domcontentloaded' })
  await expect(page.locator('#seo-skeleton')).toHaveCount(0, { timeout: 20_000 })
}

test('saving a section note sends the planner and reports it', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'note')
  const note = `noted by ${fixture.title}`

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, { plannerId: fixture.plannerId, title: fixture.title })

    await page.goto(`/planner/md/${fixture.plannerId}/edit`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })
    await settled(page)

    const notes = page.locator('.note-editor').last()
    await notes.locator('.note-editor-content').click()
    await page.keyboard.type(note)

    const request = await producesRequest(
      page,
      { method: 'PUT', url: `/api/planner/md/${fixture.plannerId}` },
      () => page.getByRole('button', { name: 'Save', exact: true }).first().click(),
    )

    expect(JSON.parse(request.postData() ?? '{}').content).toContain(note)
    await expect(page.getByText('Plan saved successfully')).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})

test('publishing a planner sends the publish flag and reports it', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'publish')

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, { plannerId: fixture.plannerId, title: fixture.title })
    await mounted(page, `/planner/md/${fixture.plannerId}`)

    const request = await producesRequest(
      page,
      { method: 'POST', url: `/api/planner/md/${fixture.plannerId}/publish` },
      () => page.getByRole('button', { name: 'Publish', exact: true }).click(),
    )

    expect(request.postData()).toBeFalsy()
    await expect(page.getByText('Plan published successfully')).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})

test('unpublishing a planner clears the publish flag and reports it', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'unpublish')

  try {
    await publish(fixture)
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, {
      plannerId: fixture.plannerId,
      title: fixture.title,
      published: true,
    })
    await mounted(page, `/planner/md/${fixture.plannerId}`)

    const request = await producesRequest(
      page,
      { method: 'POST', url: `/api/planner/md/${fixture.plannerId}/unpublish` },
      () => page.getByRole('button', { name: 'Unpublish', exact: true }).click(),
    )

    expect(request.postData()).toBeFalsy()
    await expect(page.getByText('Plan unpublished successfully')).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})

test('posting a comment reaches the server and renders', async ({ page, context, baseURL }) => {
  const fixture = await seedPublishedPlanner(baseURL!, 'comment-post')
  const body = `posted by ${fixture.title}`

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await mounted(page, `/planner/md/gesellschaft/${fixture.plannerId}`)

    const editor = page.locator('.comment-editor-content').first()
    await expect(editor).toBeVisible({ timeout: 20_000 })
    await editor.click()
    await page.keyboard.type(body)

    const request = await producesRequest(
      page,
      { method: 'POST', url: `/api/planner/${fixture.plannerId}/comments` },
      () => page.getByRole('button', { name: 'Submit', exact: true }).click(),
    )

    // The editor submits rich text, so the typed words are wrapped rather than sent verbatim.
    expect(JSON.parse(request.postData() ?? '{}').content).toContain(body)
    // A successful post raises no toast, so the rendered comment is the outcome to assert on.
    await expect(page.getByText(body)).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})

test('deleting a comment reaches the server and reports it', async ({ page, context, baseURL }) => {
  const fixture = await seedPublishedPlanner(baseURL!, 'comment-delete')
  const body = `deleted by ${fixture.title}`

  try {
    const commentId = await seedComment(fixture, body)
    await authenticateContext(context, fixture.user.id, baseURL!)
    await mounted(page, `/planner/md/gesellschaft/${fixture.plannerId}`)

    const card = page.locator(`#comment-${commentId}`)
    await expect(card).toContainText(body, { timeout: 20_000 })

    // The desktop action row renders icon-only buttons with no accessible name, and the page
    // carries a second trash icon for the planner itself — hence the scope to this card.
    await card.locator('button:has(svg.lucide-trash-2)').first().click()

    // Scoped to the dialog: the planner's own Delete button is also on the page.
    const confirm = page.getByRole('dialog').filter({ hasText: 'Delete comment?' })
    await expect(confirm).toBeVisible()

    const request = await producesRequest(
      page,
      { method: 'DELETE', url: `/api/comments/${commentId}` },
      () => confirm.getByRole('button', { name: 'Delete', exact: true }).click(),
    )

    expect(request.method()).toBe('DELETE')
    await expect(page.getByText('Comment deleted')).toBeVisible()
  } finally {
    await dropPlanner(fixture)
  }
})

test('a settings toggle sends the flipped value and reports it', async ({
  page,
  context,
  baseURL,
}) => {
  const user = await createUser('settings')
  const api = await createAuthenticatedApi(baseURL!, user.id)

  try {
    // A fresh account's syncEnabled is null, which raises a blocking first-login dialog over the
    // page; choosing for it makes the toggle's starting position deterministic too.
    const seeded = await api.put('/api/user/settings', { data: { syncEnabled: false } })
    expect(seeded.status(), await seeded.text()).toBe(200)

    await authenticateContext(context, user.id, baseURL!)
    await mounted(page, '/settings')

    const toggle = page.locator('#sync-toggle')
    await expect(toggle).toHaveAttribute('data-state', 'unchecked')

    const request = await producesRequest(page, { method: 'PUT', url: '/api/user/settings' }, () =>
      toggle.click(),
    )

    expect(JSON.parse(request.postData() ?? '{}')).toEqual({ syncEnabled: true })
    await expect(page.getByText('Sync enabled')).toBeVisible()
    await expect(toggle).toHaveAttribute('data-state', 'checked')
  } finally {
    await api.dispose()
    await deleteUser(user)
  }
})
