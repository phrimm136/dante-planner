import { createSign } from 'node:crypto'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { request as playwrightRequest } from '@playwright/test'
import type { BrowserContext, APIRequestContext } from '@playwright/test'

// docker-compose.override.yml mounts backend/src/test/resources/test-keys at /app/keys, so the
// running backend verifies against the public half of this pair.
const PRIVATE_KEY_PATH = path.resolve(
  __dirname,
  '../../backend/src/test/resources/test-keys/private_key.pem',
)

const ACCESS_TOKEN_COOKIE = 'accessToken'
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
  const signature = createSign('RSA-SHA256')
    .update(signingInput)
    .sign(readFileSync(PRIVATE_KEY_PATH, 'utf8'))

  return `${signingInput}.${base64url(signature)}`
}

export async function authenticateContext(
  context: BrowserContext,
  userId: number | bigint,
  baseURL: string,
  role: Role = 'NORMAL',
): Promise<void> {
  const { hostname } = new URL(baseURL)
  await context.addCookies([
    {
      name: ACCESS_TOKEN_COOKIE,
      value: mintAccessToken(userId, role),
      domain: hostname,
      path: '/',
      httpOnly: true,
      secure: false,
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
): Promise<APIRequestContext> {
  const { hostname } = new URL(baseURL)
  const bootstrap = await playwrightRequest.newContext({
    baseURL,
    extraHTTPHeaders: authHeaders(userId, role),
  })
  await bootstrap.get('/api/auth/me')

  const { cookies } = await bootstrap.storageState()
  const csrf = cookies.find((c) => c.name === CSRF_COOKIE)?.value
  await bootstrap.dispose()

  if (!csrf) throw new Error(`no ${CSRF_COOKIE} cookie was issued by ${baseURL}/api/auth/me`)

  return playwrightRequest.newContext({
    baseURL,
    extraHTTPHeaders: {
      ...authHeaders(userId, role),
      Cookie: `${ACCESS_TOKEN_COOKIE}=${mintAccessToken(userId, role)}; ${CSRF_COOKIE}=${csrf}`,
      'X-CSRF-Token': csrf,
    },
  })
}

export type { Role, APIRequestContext }
