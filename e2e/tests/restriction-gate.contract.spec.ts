import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { createAuthenticatedApi } from '../src/auth'
import { plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser, sql } from '../src/seed'
import { OREGON_API } from '../src/staging'

// The upsert restriction gate (docs/rfcs/0006): a banned or timed-out user writing to their
// published planner is refused 403 before validation runs, so the restriction wins over any 409
// the stale version would earn and no field moves. The same user's unpublished planners stay
// writable. Restriction state is the two columns on users; the suite seeds them directly and
// asserts through the API.

test.afterAll(closeSeedPool)

function deviceCookie(): string {
  return `deviceId=${randomUUID()}`
}

async function banUser(userId: number): Promise<void> {
  await sql('UPDATE users SET banned_at = NOW(6) WHERE id = ?', [userId])
}

async function timeoutUser(userId: number): Promise<void> {
  await sql('UPDATE users SET timeout_until = DATE_ADD(NOW(6), INTERVAL 1 HOUR) WHERE id = ?', [
    userId,
  ])
}

test('a ban refuses a stale write to a published planner before the version check', async ({
  baseURL,
}) => {
  const user = await createUser('gate-ban')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie())
  // Stored-state reads go to the write region; a Seoul read that finds a stale replica row
  // returns it without the re-check, which only fires on a miss.
  const reader = await createAuthenticatedApi(OREGON_API, user.id, 'NORMAL', deviceCookie())
  const plannerId = randomUUID()
  const title = `e2e gate-ban ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    const before = await reader.get(`/api/planner/md/${plannerId}`)
    expect(before.status(), await before.text()).toBe(200)
    const storedBefore = await before.json()

    await banUser(user.id)

    // Stale version AND changed title: were validation to run first this would be a 409, so the
    // 403 is proof the restriction gate sits in front of it.
    const refused = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, `${title} edited`), syncVersion: ackedVersion - 1 || 1 },
    })
    expect(refused.status(), await refused.text()).toBe(403)
    expect((await refused.json()).code).toBe('USER_BANNED')

    await sql('UPDATE users SET banned_at = NULL WHERE id = ?', [user.id])
    const after = await reader.get(`/api/planner/md/${plannerId}`)
    expect(after.status(), await after.text()).toBe(200)
    const storedAfter = await after.json()
    expect(storedAfter.syncVersion, 'the refused write moved the version').toBe(
      storedBefore.syncVersion,
    )
    expect(storedAfter.title, 'the refused write moved the title').toBe(storedBefore.title)
  } finally {
    await reader.dispose()
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('a banned user still writes their unpublished planner and the version advances', async ({
  baseURL,
}) => {
  const user = await createUser('gate-draft')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie())
  const plannerId = randomUUID()
  const title = `e2e gate-draft ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    await banUser(user.id)

    const written = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, `${title} edited`), syncVersion: ackedVersion },
    })
    expect(written.status(), await written.text()).toBe(200)
    expect((await written.json()).syncVersion).toBe(ackedVersion + 1)
  } finally {
    await sql('UPDATE users SET banned_at = NULL WHERE id = ?', [user.id])
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('a timeout refuses the published write with its own code', async ({ baseURL }) => {
  const user = await createUser('gate-timeout')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie())
  const plannerId = randomUUID()
  const title = `e2e gate-timeout ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    const published = await api.put(`/api/planner/md/${plannerId}/publish`, {
      data: { published: true },
    })
    expect(published.status(), await published.text()).toBe(200)

    await timeoutUser(user.id)

    const refused = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, `${title} edited`), syncVersion: ackedVersion },
    })
    expect(refused.status(), await refused.text()).toBe(403)
    expect((await refused.json()).code).toBe('USER_TIMED_OUT')
  } finally {
    await sql('UPDATE users SET timeout_until = NULL WHERE id = ?', [user.id])
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
