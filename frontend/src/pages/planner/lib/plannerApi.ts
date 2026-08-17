/**
 * Planner API Client
 *
 * Provides typed API methods for planner CRUD operations with Zod validation.
 * All responses are validated at runtime to ensure type safety.
 */

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

/**
 * Planner API methods for server synchronization
 * All methods validate responses with Zod schemas
 */
export const plannerApi = {
  /**
   * List planners for the current user (single page)
   *
   * @param page - Page number (0-indexed)
   * @param size - Number of items per page
   * @returns Paginated response with content and whether this is the final page
   */
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

  /**
   * List ALL planners for the current user (loops through all pages), tombstones included:
   * sync is the one consumer that must learn of deletions, and the deletedAt each tombstone
   * carries is what routes it to a purge instead of a pull.
   *
   * @returns Array of all planner summaries
   */
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

  /**
   * Get a single planner by ID
   *
   * @param id - Planner UUID
   * @returns Full planner data including content
   */
  async get(id: PlannerId | string): Promise<ServerPlannerResponse> {
    const data = await ApiClient.get(`${PLANNERS_BASE}/${id}`)
    return validateData(data, ServerPlannerResponseSchema, 'planner get')
  },

  /**
   * Fetch several planners, chunked to the server's id cap, one chunk per yield.
   * A chunk is parsed whole, so a malformed row fails its chunk loudly; yielding
   * per chunk lets the caller keep rows it already received when a later chunk
   * fails. The response is a bare array, not positionally aligned with the
   * request: ids naming nothing, a deleted planner, or another user's planner
   * are simply absent.
   *
   * @param ids - Planner UUIDs to fetch
   * @yields The planners the caller may see, one chunk at a time
   */
  async *batchChunks(ids: string[]): AsyncGenerator<ServerPlannerResponse[]> {
    for (let i = 0; i < ids.length; i += BATCH_PULL_MAX_IDS) {
      const data = await ApiClient.post(`${PLANNERS_BASE}/batch`, {
        ids: ids.slice(i, i + BATCH_PULL_MAX_IDS),
      })
      yield validateData(data, ServerPlannerBatchResponseSchema, 'planner batch')
    }
  },

  /**
   * Upsert a planner (create if not exists, update if exists)
   * Idempotent sync endpoint with optimistic locking
   *
   * @param id - Planner UUID
   * @param request - Full planner data including syncVersion
   * @param force - If true, bypasses syncVersion check (for conflict override)
   * @returns Created or updated planner with new syncVersion
   * @throws ConflictError if syncVersion does not match (HTTP 409)
   */
  async upsert(
    id: PlannerId | string,
    request: UpsertPlannerRequest,
    force?: boolean,
  ): Promise<ServerPlannerResponse> {
    const endpoint = force ? `${PLANNERS_BASE}/${id}?force=true` : `${PLANNERS_BASE}/${id}`
    const data = await ApiClient.put(endpoint, request)
    return validateData(data, ServerPlannerResponseSchema, 'planner upsert')
  },

  /**
   * Delete a planner
   *
   * @param id - Planner UUID
   */
  async delete(id: PlannerId | string): Promise<void> {
    await ApiClient.delete(`${PLANNERS_BASE}/${id}`)
  },

  /**
   * Bulk import planners from local storage
   *
   * @param request - Array of planners to import
   * @returns Import result with count and created planner summaries
   */
  async import(request: ImportPlannersRequest): Promise<ImportPlannersResponse> {
    const data = await ApiClient.post(`${PLANNERS_BASE}/import`, request)
    return validateData(data, ImportPlannersResponseSchema, 'planner import')
  },

  /**
   * Publish a planner.
   * Idempotent: a repeated request leaves the planner where it already is.
   *
   * @param id - Planner UUID
   * @returns The published planner
   */
  async publish(id: PlannerId | string): Promise<ServerPlannerResponse> {
    const data = await ApiClient.post(`${PLANNERS_BASE}/${id}/publish`)
    return validateData(data, ServerPlannerResponseSchema, 'planner publish')
  },

  /**
   * Withdraw a planner from public view.
   * Idempotent: a repeated request leaves the planner where it already is.
   *
   * @param id - Planner UUID
   * @returns The withdrawn planner
   */
  async unpublish(id: PlannerId | string): Promise<ServerPlannerResponse> {
    const data = await ApiClient.post(`${PLANNERS_BASE}/${id}/unpublish`)
    return validateData(data, ServerPlannerResponseSchema, 'planner unpublish')
  },
}
