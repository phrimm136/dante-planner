/**
 * Shared Google login trigger.
 *
 * The OAuth front channel is a server-driven BFF redirect: navigating to the backend's
 * `/api/auth/google/start` lets it mint `state` + PKCE and 302 to Google. The SPA and the API
 * are served from different origins, so the navigation must target the API base URL explicitly —
 * a relative path would resolve against the SPA origin, which serves no `/api/*` (a 404).
 *
 * The current page URL is passed as `returnTo` so the backend can send the user back to where
 * they started after login. The backend validates it against the origin allowlist before honoring
 * it, so an attacker cannot turn this into an open redirect.
 */

import { env } from '@/lib/env'

/** Backend BFF entry point that begins the Google OAuth redirect flow. */
const GOOGLE_LOGIN_START_PATH = '/api/auth/google/start'

/**
 * Begin Google sign-in by navigating to the backend BFF start endpoint, carrying the current
 * page URL as `returnTo` so login returns the user to where they started.
 *
 * SSR-safe: no-op on the server where `window` is undefined.
 */
export function startGoogleLogin(): void {
  if (typeof window === 'undefined') {
    return
  }
  const returnTo = encodeURIComponent(window.location.href)
  window.location.assign(`${env.VITE_API_BASE_URL}${GOOGLE_LOGIN_START_PATH}?returnTo=${returnTo}`)
}
