import { execFileSync } from 'node:child_process'
import { createSign } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { ACCESS_HEADERS } from './staging'
import { request as playwrightRequest } from '@playwright/test'
import type { BrowserContext, APIRequestContext } from '@playwright/test'

// The deployed fleet verifies against the public half of this pair, sourced from the same secret
// (deploy/base/external-secret.yaml). Fetching it needs an AWS session the runner already holds.
const PRIVATE_KEY_SECRET_ID = process.env.E2E_JWT_SECRET_ID ?? 'danteplanner/jwt/rs256-private-key'
const AWS_PROFILE = process.env.E2E_AWS_PROFILE ?? 'staging'
const AWS_REGION = process.env.E2E_AWS_REGION ?? 'us-west-2'

let cachedPrivateKey: string | undefined

function privateKey(): string {
  if (cachedPrivateKey) return cachedPrivateKey

  // A local run against compose has no reason to reach AWS, and expired SSO credentials must
  // not be the thing that stops it; the file path is the escape hatch for that case only —
  // against staging the key stays in Secrets Manager.
  const keyFile = process.env.E2E_STAGING_KEY_FILE
  if (keyFile) {
    cachedPrivateKey = readFileSync(keyFile, 'utf8')
    return cachedPrivateKey
  }

  const secret = execFileSync(
    'aws',
    [
      'secretsmanager',
      'get-secret-value',
      '--secret-id',
      PRIVATE_KEY_SECRET_ID,
      '--profile',
      AWS_PROFILE,
      '--region',
      AWS_REGION,
      '--query',
      'SecretString',
      '--output',
      'text',
    ],
    { encoding: 'utf8' },
  ).trim()

  // A PEM stored with escaped newlines reads back as literal backslash-n, which createSign rejects
  // with an opaque error rather than a parse failure.
  const pem = secret.replace(/\\n/g, '\n')
  if (!pem.includes('PRIVATE KEY')) {
    throw new Error(`${PRIVATE_KEY_SECRET_ID} did not return a PEM private key`)
  }

  cachedPrivateKey = pem
  return pem
}

export const ACCESS_TOKEN_COOKIE = 'accessToken'
const CSRF_COOKIE = 'csrf'

type Role = 'NORMAL' | 'MODERATOR' | 'ADMIN'

function base64url(input: Buffer | string): string {
  return Buffer.from(input).toString('base64url')
}

export function mintAccessToken(userId: number | bigint, role: Role = 'NORMAL'): string {
  const issuedAt = Math.floor(Date.now() / 1000)
  const header = { alg: 'RS256', typ: 'JWT' }
  const payload = {
    sub: String(userId),
    type: 'access',
    role,
    iat: issuedAt,
    exp: issuedAt + 60 * 60,
  }

  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(payload))}`
  const signature = createSign('RSA-SHA256').update(signingInput).sign(privateKey())

  return `${signingInput}.${base64url(signature)}`
}

export async function authenticateContext(
  context: BrowserContext,
  userId: number | bigint,
  baseURL: string,
  role: Role = 'NORMAL',
): Promise<void> {
  // The cookie must live on the API hostname, not the page's: the browser sends it on the
  // SPA's cross-origin fetches, which under https requires Secure + SameSite=None.
  // Only the SPA base is substituted: a caller naming a region-pinned API host means it.
  const apiBase =
    baseURL === process.env.E2E_BASE_URL ? (process.env.E2E_API_URL ?? baseURL) : baseURL
  const { hostname, protocol } = new URL(apiBase)
  const https = protocol === 'https:'
  await context.addCookies([
    {
      name: ACCESS_TOKEN_COOKIE,
      value: mintAccessToken(userId, role),
      domain: hostname,
      path: '/',
      httpOnly: true,
      secure: https,
      ...(https ? { sameSite: 'None' as const } : {}),
    },
  ])
}

export function authHeaders(userId: number | bigint, role: Role = 'NORMAL'): Record<string, string> {
  return { Cookie: `${ACCESS_TOKEN_COOKIE}=${mintAccessToken(userId, role)}` }
}

/**
 * An API context that can mutate. Every state-changing request is rejected unless it echoes the
 * csrf cookie in a header, and that cookie is only issued on a prior response, so the context has
 * to make one read before it can write.
 */
export async function createAuthenticatedApi(
  baseURL: string,
  userId: number | bigint,
  role: Role = 'NORMAL',
  extraCookies = '',
): Promise<APIRequestContext> {
  // Callers pass the page's base, but /api lives on its own hostname wherever the SPA and
  // API are split (staging, prod); the SPA host would answer the CSRF bootstrap with HTML.
  // Only the SPA base is substituted: a caller naming a region-pinned API host means it.
  const apiBase =
    baseURL === process.env.E2E_BASE_URL ? (process.env.E2E_API_URL ?? baseURL) : baseURL
  const { hostname } = new URL(apiBase)
  const bootstrap = await playwrightRequest.newContext({
    baseURL: apiBase,
    extraHTTPHeaders: { ...ACCESS_HEADERS, ...authHeaders(userId, role) },
  })
  await bootstrap.get('/api/auth/me')

  const { cookies } = await bootstrap.storageState()
  const csrf = cookies.find((c) => c.name === CSRF_COOKIE)?.value
  await bootstrap.dispose()

  if (!csrf) throw new Error(`no ${CSRF_COOKIE} cookie was issued by ${apiBase}/api/auth/me`)

  // An explicit Cookie header replaces the context's jar on every request, so cookies the
  // server sets in responses (deviceId among them) are never sent back; a caller that needs a
  // stable one passes it here.
  const cookieSuffix = extraCookies ? `; ${extraCookies}` : ''
  return playwrightRequest.newContext({
    baseURL: apiBase,
    extraHTTPHeaders: {
      ...ACCESS_HEADERS,
      ...authHeaders(userId, role),
      Cookie: `${ACCESS_TOKEN_COOKIE}=${mintAccessToken(userId, role)}; ${CSRF_COOKIE}=${csrf}${cookieSuffix}`,
      'X-CSRF-Token': csrf,
    },
  })
}

export type { Role, APIRequestContext }
