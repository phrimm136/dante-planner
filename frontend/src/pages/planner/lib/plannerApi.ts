/**
 * Planner API Client
 *
 * Provides typed API methods for planner CRUD operations with Zod validation.
 * All responses are validated at runtime to ensure type safety.
 */

import { ApiClient } from '@/lib/api'
import {
  ServerPlannerResponseSchema,
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
  async list(page = 0, size = 100): Promise<{ content: ServerPlannerSummary[]; last: boolean }> {
    const data = await ApiClient.get(`${PLANNERS_BASE}?page=${page}&size=${size}`)
    const parsed = ServerPlannerSummaryPageSchema.parse(data)
    return {
      content: parsed.content,
      last: parsed.page.number >= parsed.page.totalPages - 1,
    }
  },

  /**
   * List ALL planners for the current user (loops through all pages)
   * Use for sync operations that need complete planner list
   *
   * @returns Array of all planner summaries
   */
  async listAll(): Promise<ServerPlannerSummary[]> {
    const allPlanners: ServerPlannerSummary[] = []
    let page = 0
    let hasMore = true

    while (hasMore) {
      const result = await this.list(page, 100)
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
    return ServerPlannerResponseSchema.parse(data)
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
    return ServerPlannerResponseSchema.parse(data)
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
    return ImportPlannersResponseSchema.parse(data)
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
    return ServerPlannerResponseSchema.parse(data)
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
    return ServerPlannerResponseSchema.parse(data)
  },
}
