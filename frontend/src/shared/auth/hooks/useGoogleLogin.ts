import { env } from '@/lib/env'

const GOOGLE_LOGIN_START_PATH = '/api/auth/google/start'

export function startGoogleLogin(): void {
  if (typeof window === 'undefined') {
    return
  }
  const returnTo = encodeURIComponent(window.location.href)
  window.location.assign(`${env.VITE_API_BASE_URL}${GOOGLE_LOGIN_START_PATH}?returnTo=${returnTo}`)
}
