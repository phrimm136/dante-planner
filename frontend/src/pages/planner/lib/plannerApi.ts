import { ApiClient } from '@/lib/api'
import { BATCH_PULL_MAX_IDS } from '@/lib/constants'
import { validateData } from '@/lib/validation'
import {
  ServerPlannerResponseSchema,
  ServerPlannerBatchResponseSchema,
  ServerPlannerSummaryPageSchema,
  ImportPlannersResponseSchema,
} from '../schemas/PlannerSchemas'
import type {
  UpsertPlannerRequest,
  ImportPlannersRequest,
  ServerPlannerResponse,
  ServerPlannerSummary,
  ImportPlannersResponse,
  PlannerId,
} from '../types/PlannerTypes'

const PLANNERS_BASE = '/api/planner/md'

export const plannerApi = {
  async list(
    page = 0,
    size = 100,
    includeDeleted = false,
  ): Promise<{ content: ServerPlannerSummary[]; last: boolean }> {
    const deletedParam = includeDeleted ? '&includeDeleted=true' : ''
    const data = await ApiClient.get(`${PLANNERS_BASE}?page=${page}&size=${size}${deletedParam}`)
    const parsed = validateData(data, ServerPlannerSummaryPageSchema, 'planner list')
    return {
      content: parsed.content,
      last: parsed.page.number >= parsed.page.totalPages - 1,
    }
  },

  async listAll(): Promise<ServerPlannerSummary[]> {
    const allPlanners: ServerPlannerSummary[] = []
    let page = 0
    let hasMore = true

    while (hasMore) {
      const result = await this.list(page, 100, true)
      allPlanners.push(...result.content)
      hasMore = !result.last
      page++
    }

    return allPlanners
  },

  async get(id: PlannerId | string): Promise<ServerPlannerResponse> {
    const data = await ApiClient.get(`${PLANNERS_BASE}/${id}`)
    return validateData(data, ServerPlannerResponseSchema, 'planner get')
  },

  async *batchChunks(ids: string[]): AsyncGenerator<ServerPlannerResponse[]> {
    for (let i = 0; i < ids.length; i += BATCH_PULL_MAX_IDS) {
      const data = await ApiClient.post(`${PLANNERS_BASE}/batch`, {
        ids: ids.slice(i, i + BATCH_PULL_MAX_IDS),
      })
      yield validateData(data, ServerPlannerBatchResponseSchema, 'planner batch')
    }
  },

  async upsert(
    id: PlannerId | string,
    request: UpsertPlannerRequest,
    force?: boolean,
  ): Promise<ServerPlannerResponse> {
    const endpoint = force ? `${PLANNERS_BASE}/${id}?force=true` : `${PLANNERS_BASE}/${id}`
    const data = await ApiClient.put(endpoint, request)
    return validateData(data, ServerPlannerResponseSchema, 'planner upsert')
  },

  async delete(id: PlannerId | string): Promise<void> {
    await ApiClient.delete(`${PLANNERS_BASE}/${id}`)
  },

  async import(request: ImportPlannersRequest): Promise<ImportPlannersResponse> {
    const data = await ApiClient.post(`${PLANNERS_BASE}/import`, request)
    return validateData(data, ImportPlannersResponseSchema, 'planner import')
  },
}
