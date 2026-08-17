import { test, expect } from '@playwright/test'
import { request as playwrightRequest } from '@playwright/test'
import { becomesTrue } from '../src/consistency'
import { setCookieHeaders, setCookieValue } from '../src/cookies'
import {
  authorizeAtStub,
  completeCallback,
  landedOnLoginError,
  oauthEmail,
  startFlow,
} from '../src/oauth'
import { closeSeedPool, deleteUser, findUserByEmail } from '../src/seed'
import { APP_URL, SEOUL_API } from '../src/staging'

// The session after login (docs/rfcs/0006 token lineage): a live refresh token resurrects a
// session and rotates, and logout revokes the whole family — after it, neither the access token
// nor the rotated family head buys anything. One stub login serves the whole test because every
// callback spends a token of the per-IP AUTH rate bucket.

test.afterAll(closeSeedPool)

const ACCESS = 'accessToken'
const REFRESH = 'refreshToken'
const CSRF = 'csrf'

async function probe(cookies: string): Promise<{ status: number; body: string; raw: string[] }> {
  const context = await playwrightRequest.newContext({
    baseURL: SEOUL_API,
    extraHTTPHeaders: { Cookie: cookies },
  })
  try {
    const response = await context.get('/api/auth/me')
    return {
      status: response.status(),
      body: await response.text(),
      raw: setCookieHeaders(response),
    }
  } finally {
    await context.dispose()
  }
}

test('a refresh token rotates on use and dies with its family at logout', async () => {
  const email = oauthEmail('lineage')

  try {
    const flow = await startFlow(APP_URL)
    const authorized = await authorizeAtStub(flow.authorizeUrl, email)
    const outcome = await completeCallback(authorized.callbackUrl, flow.oauthTx, {
      host: SEOUL_API,
    })
    expect(landedOnLoginError(outcome), `landed on ${outcome.location}`).toBe(false)

    const access = setCookieValue(outcome.setCookies, ACCESS)
    const refresh = setCookieValue(outcome.setCookies, REFRESH)
    expect(access, 'login minted no access cookie').not.toBeNull()
    expect(refresh, 'login minted no refresh cookie').not.toBeNull()

    // A live refresh alone resurrects the session, and using it rotates the family head. The
    // probe is a liveness wait: the callback committed the user row on the primary, and the
    // refresh path's user lookup reads the replica, which answers "no such user" until the row
    // replicates. A refused probe spends nothing, so polling is safe.
    let resurrected = await probe(`${REFRESH}=${refresh}`)
    await becomesTrue(
      async () => {
        if (resurrected.status === 200) return true
        resurrected = await probe(`${REFRESH}=${refresh}`)
        return resurrected.status === 200
      },
      { what: `the refresh resurrecting ${email} once the user row replicates` },
    )
    const rotatedRefresh = setCookieValue(resurrected.raw, REFRESH)
    const rotatedAccess = setCookieValue(resurrected.raw, ACCESS)
    expect(rotatedRefresh, 'the refresh was not rotated').not.toBeNull()
    expect(rotatedRefresh).not.toBe(refresh)
    expect(rotatedAccess, 'no new access token rode the refresh').not.toBeNull()

    // Logout needs the csrf pair, minted on any prior response.
    const csrfBootstrap = await probe(`${ACCESS}=${rotatedAccess}`)
    const csrf = setCookieValue(csrfBootstrap.raw, CSRF)
    expect(csrf, 'no csrf cookie was issued').not.toBeNull()

    const logoutContext = await playwrightRequest.newContext({
      baseURL: SEOUL_API,
      extraHTTPHeaders: {
        Cookie: `${ACCESS}=${rotatedAccess}; ${REFRESH}=${rotatedRefresh}; ${CSRF}=${csrf}`,
        'X-CSRF-Token': csrf!,
      },
    })
    const loggedOut = await logoutContext.post('/api/auth/logout')
    expect(loggedOut.status(), await loggedOut.text()).toBeLessThan(300)
    await logoutContext.dispose()

    // The family is revoked: the access token answers anonymous, and the rotated family head
    // resurrects nothing.
    const deadAccess = await probe(`${ACCESS}=${rotatedAccess}`)
    expect(deadAccess.status, `a logged-out access token still answered: ${deadAccess.body}`).toBe(
      204,
    )
    const deadRefresh = await probe(`${REFRESH}=${rotatedRefresh}`)
    expect(
      deadRefresh.status,
      `a revoked family still resurrected a session: ${deadRefresh.body}`,
    ).toBe(204)
  } finally {
    const user = await findUserByEmail(email)
    if (user) await deleteUser(user)
  }
})
