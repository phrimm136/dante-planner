import { defineConfig, devices } from '@playwright/test'

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
  },

  projects: [
    {
      // No browser: these use the request fixture alone.
      name: 'contract',
      testMatch: /.*\.contract\.spec\.ts/,
    },
    {
      name: 'chromium',
      testIgnore: /.*\.contract\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
