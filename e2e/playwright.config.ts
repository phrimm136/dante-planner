import { defineConfig, devices } from '@playwright/test'
import { ACCESS_HEADERS } from './src/staging'

// One origin for SPA and API alike, served by the nginx in docker-compose.e2e.yml.
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost'

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list']],

  use: {
    baseURL,
    trace: 'on-first-retry',
    ignoreHTTPSErrors: false,
    // Cloudflare Access challenges every deployed hostname, so the service token rides each
    // request. Empty locally, where nothing is in front of the stack.
    extraHTTPHeaders: ACCESS_HEADERS,
  },

  projects: [
    {
      // No browser: these use the request fixture alone. Their relative /api paths must
      // resolve against the API host — in staging the SPA and API hostnames differ, and
      // the SPA's fallback would answer /api requests with index.html.
      name: 'contract',
      testMatch: /.*\.contract\.spec\.ts/,
      use: {
        baseURL: process.env.E2E_API_URL ?? process.env.E2E_BASE_URL ?? 'http://localhost',
      },
    },
    {
      // Infrastructure suites: request fixture only, degradation/steering assertions. Same
      // API-host baseURL logic as contract, and for the same SPA-fallback reason.
      name: 'infra',
      testMatch: /.*\.infra\.spec\.ts/,
      use: {
        baseURL: process.env.E2E_API_URL ?? process.env.E2E_BASE_URL ?? 'http://localhost',
      },
    },
    {
      name: 'chromium',
      testIgnore: /.*\.(contract|infra)\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
