import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { createAuthenticatedApi } from '../src/auth'
import { plannerPayload } from '../src/plannerContent'
import { closeSeedPool, createUser, deleteUser } from '../src/seed'
import { OREGON_API } from '../src/staging'

// Server-side no-op conflict arbitration (docs/rfcs/0005): a stale-version upsert whose
// client-mutable fields and content are tree-equal to the stored row is answered 200 with the
// stored syncVersion and writes nothing — the retry of a committed save must not open a conflict
// dialog. Anything actually different, and a missing version on an existing row, still 409s.
//
// The device stamp is inside the predicate (docs/adr/085): only a same-device retry can
// arbitrate, so every context here pins a deviceId cookie — the server mints a fresh one per
// cookieless request, and a fresh stamp alone defeats the no-op.

test.afterAll(closeSeedPool)

function deviceCookie(device: string): string {
  return `deviceId=${device}`
}

/** The stored content with its top-level keys re-inserted in reverse, same tree. */
function reorderedContent(content: string): string {
  const parsed = JSON.parse(content) as Record<string, unknown>
  const reversed: Record<string, unknown> = {}
  for (const key of Object.keys(parsed).reverse()) reversed[key] = parsed[key]
  return JSON.stringify(reversed)
}

test('a retried committed save answers 200 with the stored version and writes nothing', async ({
  baseURL,
}) => {
  const user = await createUser('noop-retry')
  const device = randomUUID()
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie(device))
  // Stored-state reads go to the write region: a Seoul by-id read that FINDS a row on the
  // stale replica returns it as-is (the re-check fires only on a miss), so a "no write
  // happened" assertion read through Seoul races replication.
  const reader = await createAuthenticatedApi(OREGON_API, user.id, 'NORMAL', deviceCookie(device))
  const plannerId = randomUUID()
  const title = `e2e noop-retry ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    // The committed save: presented version matches, the server advances it.
    const committedRequest = { ...plannerPayload(plannerId, title), syncVersion: ackedVersion }
    const committed = await api.put(`/api/planner/md/${plannerId}`, { data: committedRequest })
    expect(committed.status(), await committed.text()).toBe(200)
    const committedBody = await committed.json()
    expect(committedBody.syncVersion).toBeGreaterThan(ackedVersion)

    // The stored row before the retry, read rather than taken from the write response: the
    // update response serializes the entity before @PreUpdate stamps lastModifiedAt at flush,
    // so only a read shows what the row actually holds.
    const before = await reader.get(`/api/planner/md/${plannerId}`)
    expect(before.status(), await before.text()).toBe(200)
    const storedBefore = await before.json()

    // The retry: same bytes, now-stale version. Arbitration answers 200 with the stored state.
    const retried = await api.put(`/api/planner/md/${plannerId}`, { data: committedRequest })
    expect(retried.status(), await retried.text()).toBe(200)
    expect((await retried.json()).syncVersion).toBe(committedBody.syncVersion)

    const after = await reader.get(`/api/planner/md/${plannerId}`)
    expect(after.status(), await after.text()).toBe(200)
    const storedAfter = await after.json()
    expect(storedAfter.syncVersion).toBe(storedBefore.syncVersion)
    expect(storedAfter.lastModifiedAt, 'the no-op touched the row').toBe(
      storedBefore.lastModifiedAt,
    )
  } finally {
    await reader.dispose()
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('tree-equality is structural: reordered content keys still arbitrate to a no-op', async ({
  baseURL,
}) => {
  const user = await createUser('noop-tree')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie(randomUUID()))
  const plannerId = randomUUID()
  const title = `e2e noop-tree ${plannerId.slice(0, 8)}`

  try {
    const payload = plannerPayload(plannerId, title)
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...payload, syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    const committed = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...payload, syncVersion: ackedVersion },
    })
    expect(committed.status(), await committed.text()).toBe(200)
    const committedBody = await committed.json()

    const reordered = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...payload, content: reorderedContent(payload.content), syncVersion: ackedVersion },
    })
    expect(reordered.status(), await reordered.text()).toBe(200)
    expect((await reordered.json()).syncVersion).toBe(committedBody.syncVersion)
  } finally {
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('a stale save with real differences still 409s and names the stored version', async ({
  baseURL,
}) => {
  const user = await createUser('noop-conflict')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie(randomUUID()))
  const plannerId = randomUUID()
  const title = `e2e noop-conflict ${plannerId.slice(0, 8)}`

  try {
    const created = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: 1 },
    })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    const committed = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, title), syncVersion: ackedVersion },
    })
    expect(committed.status(), await committed.text()).toBe(200)
    const storedVersion = (await committed.json()).syncVersion as number

    const conflicted = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...plannerPayload(plannerId, `${title} edited`), syncVersion: ackedVersion },
    })
    expect(conflicted.status(), await conflicted.text()).toBe(409)
    expect((await conflicted.json()).serverVersion).toBe(storedVersion)

    // A version-less upsert of an existing row never reaches arbitration.
    const versionless = await api.put(`/api/planner/md/${plannerId}`, {
      data: plannerPayload(plannerId, title),
    })
    expect(versionless.status(), await versionless.text()).toBe(409)
  } finally {
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})

test('the device stamp defeats a cross-device identical resend', async ({ baseURL }) => {
  const user = await createUser('noop-device')
  const api = await createAuthenticatedApi(baseURL!, user.id, 'NORMAL', deviceCookie(randomUUID()))
  let otherDevice: Awaited<ReturnType<typeof createAuthenticatedApi>> | undefined
  const plannerId = randomUUID()
  const title = `e2e noop-device ${plannerId.slice(0, 8)}`

  try {
    const committedRequest = { ...plannerPayload(plannerId, title), syncVersion: 1 }
    const created = await api.put(`/api/planner/md/${plannerId}`, { data: committedRequest })
    expect([200, 201], await created.text()).toContain(created.status())
    const ackedVersion = (await created.json()).syncVersion as number

    const advanced = await api.put(`/api/planner/md/${plannerId}`, {
      data: { ...committedRequest, syncVersion: ackedVersion },
    })
    expect(advanced.status(), await advanced.text()).toBe(200)

    // Identical bytes, stale version, different device: restamping device_id is a field move,
    // so this is a real conflict (docs/adr/085), not an ackable no-op.
    otherDevice = await createAuthenticatedApi(
      baseURL!,
      user.id,
      'NORMAL',
      deviceCookie(randomUUID()),
    )
    const crossDevice = await otherDevice.put(`/api/planner/md/${plannerId}`, {
      data: { ...committedRequest, syncVersion: ackedVersion },
    })
    expect(crossDevice.status(), await crossDevice.text()).toBe(409)
  } finally {
    await otherDevice?.dispose()
    await api.delete(`/api/planner/md/${plannerId}`)
    await api.dispose()
    await deleteUser(user)
  }
})
