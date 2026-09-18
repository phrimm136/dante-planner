import type { z } from 'zod'
import type { MDCategory, RRCategory, PlannerType } from '@/shared/gameData'
import type {
  PublicPlannerSchema,
  PaginatedPlannersSchema,
  VoteResponseSchema,
  SubscriptionResponseSchema,
  PublishedPlannerDetailSchema,
} from '../schemas/PlannerListSchemas'

export type { MDCategory, RRCategory, PlannerType }

export type PlannerListView = 'my-plans' | 'community'

export type PlannerSortOption = 'recent' | 'popular' | 'votes'

export type PublicPlanner = z.infer<typeof PublicPlannerSchema>

export type PaginatedPlanners = z.infer<typeof PaginatedPlannersSchema>

export type VoteResponse = z.infer<typeof VoteResponseSchema>

export type SubscriptionResponse = z.infer<typeof SubscriptionResponseSchema>

export type PublishedPlannerDetail = z.infer<typeof PublishedPlannerDetailSchema>
