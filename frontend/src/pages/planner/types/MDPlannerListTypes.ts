import type { MDCategory } from '@/shared/gameData'

export type MDGesellschaftMode = 'published' | 'best'

/**
 * URL search params for /planner/md route (personal planners)
 * Minimal params - only category filter and pagination
 *
 * Note: Defaults (page=0, no category, empty q) are hidden from URL via
 * TanStack Router's default param omission behavior.
 */
export interface MDUserSearchParams {
  category?: MDCategory | undefined
  page?: number
  q?: string
}

/**
 * URL search params for /planner/md/gesellschaft route (community planners)
 * Includes mode parameter to switch between all published and best planners
 *
 * Note: Defaults (page=0, no category, mode='published', empty q) are hidden from URL.
 */
export interface MDGesellschaftSearchParams {
  category?: MDCategory | undefined
  page?: number
  mode?: MDGesellschaftMode
  q?: string
  keyword?: string
  identity?: string
  ego?: string
  gift?: string
  themePack?: string
}

export interface MDGesellschaftFilters {
  category: MDCategory | undefined
  page: number
  mode: MDGesellschaftMode
  search: string
  keyword: string | undefined
  identity: string | undefined
  ego: string | undefined
  gift: string | undefined
  themePack: string | undefined
}
