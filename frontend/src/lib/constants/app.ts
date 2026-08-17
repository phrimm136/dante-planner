/**
 * App-wide configuration that belongs to no single feature.
 */

/**
 * Search bar debounce delay in milliseconds
 */
export const SEARCH_DEBOUNCE_DELAY = 100

/**
 * Discord server invite URL
 */
export const DISCORD_INVITE_URL = 'https://discord.gg/Z6DWySQ7B2'

/**
 * Contact email shown in the footer and legal pages
 */
export const CONTACT_EMAIL = 'contact@dante-planner.com'

/**
 * Number of announcements shown as preview on the home page
 */
export const ANNOUNCEMENT_PREVIEW_COUNT = 5

/**
 * i18n language code to BCP 47 locale mapping
 * Used for date/time formatting with toLocaleString()
 */
export const I18N_LOCALE_MAP: Record<string, string> = {
  KR: 'ko-KR',
  JP: 'ja-JP',
  CN: 'zh-CN',
  EN: 'en-US',
} as const

/**
 * Boolean filter options (N/Y) for toggle filters like fusioned, theme pack exclusive
 */
export const BOOLEAN_FILTER_OPTIONS = ['N', 'Y'] as const
