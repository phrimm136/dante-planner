import { request as playwrightRequest } from '@playwright/test'
import { setCookieHeaders, setCookieValue } from './cookies'
import { API_URL, IDP_URL } from './staging'

export const OAUTH_TX_COOKIE = 'oauth_tx'
export const LOGIN_ERROR_PATH = '/?login=error'

/** The identity the stub mints when the authorize request carries no login_hint. */
export const STUB_DEFAULT_EMAIL = 'e2e-user@example.invalid'

export function oauthEmail(label: string): string {
  return `e2e-oauth-${label}-${process.pid}-${process.hrtime.bigint()}@example.test`
}

export interface StartedFlow {
  authorizeUrl: string
  oauthTx: string
  state: string
}

/**
 * Drives {@code GET /api/auth/google/start} without following its redirect, so the caller holds
 * the sealed transaction cookie and the authorize URL the browser would have been sent to.
 */
export async function startFlow(returnTo: string): Promise<StartedFlow> {
  const context = await playwrightRequest.newContext()
  try {
    const started = await context.get(
      `${API_URL}/api/auth/google/start?returnTo=${encodeURIComponent(returnTo)}`,
      { maxRedirects: 0 },
    )
    if (started.status() !== 302) {
      throw new Error(`/api/auth/google/start answered ${started.status()}`)
    }

    const authorizeUrl = started.headers()['location']
    const oauthTx = setCookieValue(setCookieHeaders(started), OAUTH_TX_COOKIE)
    if (!authorizeUrl || !oauthTx) {
      throw new Error('/api/auth/google/start set no location or no oauth_tx')
    }

    const state = new URL(authorizeUrl).searchParams.get('state')
    if (!state) throw new Error(`authorize url carries no state: ${authorizeUrl}`)

    return { authorizeUrl, oauthTx, state }
  } finally {
    await context.dispose()
  }
}

export interface StubAuthorization {
  callbackUrl: string
  code: string
  state: string | null
}

/**
 * Consents at the stub IdP on behalf of one identity. The stub auto-redirects, so this returns the
 * callback URL the browser would have followed, code and all.
 */
export async function authorizeAtStub(
  authorizeUrl: string,
  loginHint?: string,
): Promise<StubAuthorization> {
  const url = new URL(authorizeUrl)
  if (loginHint) url.searchParams.set('login_hint', loginHint)

  const context = await playwrightRequest.newContext()
  try {
    const authorized = await context.get(url.toString(), { maxRedirects: 0 })
    if (authorized.status() !== 302) {
      throw new Error(`${IDP_URL}/auth answered ${authorized.status()}: ${await authorized.text()}`)
    }

    const callbackUrl = authorized.headers()['location']
    if (!callbackUrl) throw new Error(`${IDP_URL}/auth set no location`)

    const params = new URL(callbackUrl).searchParams
    const code = params.get('code')
    if (!code) throw new Error(`${IDP_URL}/auth minted no code`)

    return { callbackUrl, code, state: params.get('state') }
  } finally {
    await context.dispose()
  }
}

export interface CallbackOverrides {
  code?: string
  state?: string
  /** Serves the callback from one named region rather than the shared API hostname. */
  host?: string
}

export interface CallbackOutcome {
  status: number
  location: string | null
  setCookies: string[]
}

/**
 * Presents a callback to the API with a chosen transaction cookie, optionally rewriting the code,
 * the state, or the region that serves it.
 */
export async function completeCallback(
  callbackUrl: string,
  oauthTx: string,
  overrides: CallbackOverrides = {},
): Promise<CallbackOutcome> {
  const url = new URL(callbackUrl)
  if (overrides.code !== undefined) url.searchParams.set('code', overrides.code)
  if (overrides.state !== undefined) url.searchParams.set('state', overrides.state)
  if (overrides.host) {
    const host = new URL(overrides.host)
    url.protocol = host.protocol
    url.host = host.host
  }

  const context = await playwrightRequest.newContext({
    extraHTTPHeaders: { Cookie: `${OAUTH_TX_COOKIE}=${oauthTx}` },
  })
  try {
    const response = await context.get(url.toString(), { maxRedirects: 0 })
    return {
      status: response.status(),
      location: response.headers()['location'] ?? null,
      setCookies: setCookieHeaders(response),
    }
  } finally {
    await context.dispose()
  }
}

export function landedOnLoginError(outcome: CallbackOutcome): boolean {
  return outcome.status === 302 && (outcome.location ?? '').endsWith(LOGIN_ERROR_PATH)
}
