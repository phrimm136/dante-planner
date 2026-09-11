import { test, expect } from '../src/browser'
import { authenticateContext } from '../src/auth'
import { seedLocalPlanner } from '../src/localPlanner'
import { dropPlanner, seedPlanner } from '../src/plannerFixture'
import { closeSeedPool } from '../src/seed'

// The autosave invariant: editing writes IndexedDB only, the server PUT belongs to the manual
// Save button alone. Sync is ON here on purpose — with it off, a push is impossible and the
// no-request half proves nothing.

test.afterAll(closeSeedPool)

test('an edit reaches IndexedDB only, and survives an immediate reload as a draft', async ({
  page,
  context,
  baseURL,
}) => {
  const fixture = await seedPlanner(baseURL!, 'draft-local')
  const editedText = `draft only ${fixture.plannerId.slice(0, 8)}`

  try {
    await authenticateContext(context, fixture.user.id, baseURL!)
    await seedLocalPlanner(page, { plannerId: fixture.plannerId, title: fixture.title })

    await page.goto(`/planner/md/${fixture.plannerId}/edit`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })

    const writes: string[] = []
    page.on('request', (request) => {
      if (request.method() !== 'GET' && request.url().includes('/api/planner/md/')) {
        writes.push(`${request.method()} ${request.url()}`)
      }
    })

    await page.locator('.note-editor-content').last().click()
    await page.keyboard.type(editedText)

    // No wait between the last keystroke and the reload: the write is issued inside the
    // keystroke's own task, so nothing is left for a timer to lose.
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: 'Closing Notes' })).toBeVisible({
      timeout: 20_000,
    })
    await expect(page.getByText(editedText)).toBeVisible()
    expect(writes, 'an edit reached the network without the Save button').toEqual([])
  } finally {
    await dropPlanner(fixture)
  }
})
