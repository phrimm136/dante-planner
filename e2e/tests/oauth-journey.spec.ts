import { test, expect } from '@playwright/test'
import { ACCESS_TOKEN_COOKIE } from '../src/auth'
import { setCookieValue } from '../src/cookies'
import { RYW_COOKIE } from '../src/gtid'
import {
  STUB_DEFAULT_EMAIL,
  authorizeAtStub,
  completeCallback,
  landedOnLoginError,
  oauthEmail,
  startFlow,
} from '../src/oauth'
import { closeSeedPool, deleteUser, findUserByEmail } from '../src/seed'
import { APP_URL, SEOUL_API } from '../src/staging'

// Login end to end against the stub IdP, then the refusals that only exist at HTTP level: a
// browser cannot present a tampered state, a spent code, or a code minted under another
// transaction, but an attacker can.
//
// Every callback here spends one token of the AUTH rate-limit bucket, which is keyed by client IP
// and holds five per minute by default — staging raises rate-limit.auth.capacity for this suite.

test.describe.configure({ mode: 'default' })

test.afterAll(closeSeedPool)

async function deleteAccountFor(email: string): Promise<void> {
  const user = await findUserByEmail(email)
  if (user) await deleteUser(user)
}

// The header account menu labels its trigger and its Google item through i18n, so both locales the
// app may resolve are matched.
const ACCOUNT_MENU = /Sign in|로그인/
const GOOGLE_LOGIN_ITEM = /Google/
const SYNC_PROMPT_DECLINE = /Keep Local Only|로컬에만/

test('a visitor signs in through the stub IdP and comes back logged in', async ({ page }) => {
  // Four edge round trips (SPA load, authorize, callback, return) on a cold JIT and cold
  // Cloudflare cache; the default budget measures warmup, not the property.
  test.setTimeout(90_000)
  try {
    await page.goto(APP_URL, { waitUntil: 'domcontentloaded' })

    // The account trigger is an icon button whose accessible name comes from an aria-label
    // React attaches on mount, so it does not exist at domcontentloaded — clicking without
    // waiting for it resolves nothing and the journey stalls until the test budget expires.
    const accountMenu = page.getByRole('button', { name: ACCOUNT_MENU }).first()
    await expect(accountMenu).toBeVisible({ timeout: 20_000 })
    await accountMenu.click()
    await page.getByRole('menuitem', { name: GOOGLE_LOGIN_ITEM }).click()

    await page.waitForURL((url) => url.origin === new URL(APP_URL).origin, { timeout: 30_000 })

    // A first login lands on the sync-choice prompt, which covers the header until answered;
    // its presence IS the proof the callback authenticated, since it renders for signed-in
    // users only. Answering it releases the header for the identity assertion.
    const syncChoice = page.getByRole('button', { name: SYNC_PROMPT_DECLINE })
    await expect(syncChoice).toBeVisible({ timeout: 20_000 })
    await syncChoice.click()

    const signedInMenu = page.getByRole('button', { name: ACCOUNT_MENU }).first()
    await expect(signedInMenu).toBeVisible({ timeout: 20_000 })
    await signedInMenu.click()
    await expect(page.getByText(STUB_DEFAULT_EMAIL)).toBeVisible({ timeout: 20_000 })
  } finally {
    await deleteAccountFor(STUB_DEFAULT_EMAIL)
  }
})

test('the callback mints ryw_gtid for the user row it commits', async () => {
  const email = oauthEmail('ryw')

  try {
    const flow = await startFlow(APP_URL)
    const authorized = await authorizeAtStub(flow.authorizeUrl, email)

    // Served by Seoul on purpose: the GTID gate is registered only where routing is enabled, so
    // Oregon mints no cookie for the same commit.
    const outcome = await completeCallback(authorized.callbackUrl, flow.oauthTx, {
      host: SEOUL_API,
    })

    expect(outcome.status).toBe(302)
    expect(landedOnLoginError(outcome), `landed on ${outcome.location}`).toBe(false)
    expect(setCookieValue(outcome.setCookies, ACCESS_TOKEN_COOKIE)).not.toBeNull()
    expect(setCookieValue(outcome.setCookies, RYW_COOKIE)).not.toBeNull()

    expect(await findUserByEmail(email)).not.toBeNull()
  } finally {
    await deleteAccountFor(email)
  }
})

test('a tampered state on the callback is rejected', async () => {
  const email = oauthEmail('state')

  try {
    const flow = await startFlow(APP_URL)
    const authorized = await authorizeAtStub(flow.authorizeUrl, email)

    const outcome = await completeCallback(authorized.callbackUrl, flow.oauthTx, {
      state: `${authorized.state}tampered`,
    })

    expect(landedOnLoginError(outcome), `landed on ${outcome.location}`).toBe(true)
    expect(setCookieValue(outcome.setCookies, ACCESS_TOKEN_COOKIE)).toBeNull()
    expect(await findUserByEmail(email), 'a rejected callback created an account').toBeNull()
  } finally {
    await deleteAccountFor(email)
  }
})

test('a replayed code is refused as an auth error rather than a server error', async () => {
  const email = oauthEmail('replay')

  try {
    const flow = await startFlow(APP_URL)
    const authorized = await authorizeAtStub(flow.authorizeUrl, email)

    const first = await completeCallback(authorized.callbackUrl, flow.oauthTx)
    expect(first.status).toBe(302)
    expect(setCookieValue(first.setCookies, ACCESS_TOKEN_COOKIE)).not.toBeNull()

    const replay = await completeCallback(authorized.callbackUrl, flow.oauthTx)
    expect(replay.status, 'the spent code produced a server error').toBe(302)
    expect(landedOnLoginError(replay), `landed on ${replay.location}`).toBe(true)
    expect(setCookieValue(replay.setCookies, ACCESS_TOKEN_COOKIE)).toBeNull()
  } finally {
    await deleteAccountFor(email)
  }
})

test('a code minted under another transaction is refused', async () => {
  const email = oauthEmail('cross-tx')

  try {
    const donor = await startFlow(APP_URL)
    const authorized = await authorizeAtStub(donor.authorizeUrl, email)

    // The second transaction's state passes the equality check, so the refusal can only come from
    // the code exchange: the verifier sealed into this oauth_tx does not match the challenge the
    // code was minted against.
    const receiver = await startFlow(APP_URL)
    const outcome = await completeCallback(authorized.callbackUrl, receiver.oauthTx, {
      state: receiver.state,
    })

    expect(landedOnLoginError(outcome), `landed on ${outcome.location}`).toBe(true)
    expect(setCookieValue(outcome.setCookies, ACCESS_TOKEN_COOKIE)).toBeNull()
    expect(await findUserByEmail(email), 'a rejected callback created an account').toBeNull()
  } finally {
    await deleteAccountFor(email)
  }
})
