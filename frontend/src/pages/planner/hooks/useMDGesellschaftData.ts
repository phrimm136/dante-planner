import { useSuspenseQuery, queryOptions } from '@tanstack/react-query'

import { ApiClient } from '@/lib/api'
import { PLANNER_LIST, STALE_TIME } from '@/lib/constants'
import { validateData } from '@/lib/validation'
import { PaginatedPlannersSchema } from '../schemas/PlannerListSchemas'

import type { MDCategory } from '@/shared/gameData'
import type { MDGesellschaftMode } from '../types/MDPlannerListTypes'

export const gesellschaftQueryKeys = {
  all: ['gesellschaft'] as const,

  published: (params: GesellschaftQueryParams) =>
    [...gesellschaftQueryKeys.all, 'published', params] as const,

  recommended: (params: GesellschaftQueryParams) =>
    [...gesellschaftQueryKeys.all, 'recommended', params] as const,
}

interface GesellschaftQueryParams {
  page: number
  size: number
  category?: MDCategory | undefined
  search?: string | undefined
  keyword?: string | undefined
  identity?: string | undefined
  ego?: string | undefined
  gift?: string | undefined
  themePack?: string | undefined
}

function createGesellschaftQueryOptions(mode: MDGesellschaftMode, params: GesellschaftQueryParams) {
  const isBest = mode === 'best'

  return queryOptions({
    queryKey: isBest
      ? gesellschaftQueryKeys.recommended(params)
      : gesellschaftQueryKeys.published(params),
    queryFn: async ({ signal }) => {
      const searchParams = new URLSearchParams()
      searchParams.append('page', String(params.page))
      searchParams.append('size', String(params.size))
      if (params.category) searchParams.append('category', params.category)
      if (params.search) searchParams.append('q', params.search)
      if (params.keyword) searchParams.append('keyword', params.keyword)
      if (params.identity) searchParams.append('identity', params.identity)
      if (params.ego) searchParams.append('ego', params.ego)
      if (params.gift) searchParams.append('gift', params.gift)
      if (params.themePack) searchParams.append('themePack', params.themePack)

      const path = isBest ? '/api/planner/md/recommended' : '/api/planner/md/published'
      const data = await ApiClient.get(`${path}?${searchParams.toString()}`, { signal })
      return validateData(
        data,
        PaginatedPlannersSchema,
        isBest ? 'gesellschaft recommended' : 'gesellschaft published',
      )
    },
    staleTime: isBest ? STALE_TIME.MEDIUM : STALE_TIME.SHORT,
  })
}

export interface UseMDGesellschaftDataOptions {
  mode: MDGesellschaftMode
  page: number
  category?: MDCategory
  search?: string
  keyword?: string
  identity?: string
  ego?: string
  gift?: string
  themePack?: string
}

export function useMDGesellschaftData(options: UseMDGesellschaftDataOptions) {
  const { mode, page, category, search, keyword, identity, ego, gift, themePack } = options

  return useSuspenseQuery(
    createGesellschaftQueryOptions(mode, {
      page,
      size: PLANNER_LIST.PAGE_SIZE,
      category,
      search,
      keyword,
      identity,
      ego,
      gift,
      themePack,
    }),
  )
}
