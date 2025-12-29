/**
 * Planner API Client
 *
 * Provides typed API methods for planner CRUD operations with Zod validation.
 * All responses are validated at runtime to ensure type safety.
 */

import { ApiClient } from './api'
import {
  ServerPlannerResponseSchema,
  ServerPlannerSummaryArraySchema,
  ImportPlannersResponseSchema,
} from '@/schemas/PlannerSchemas'
import type {
  CreatePlannerRequest,
  UpdatePlannerRequest,
  ImportPlannersRequest,
  ServerPlannerResponse,
  ServerPlannerSummary,
  ImportPlannersResponse,
  PlannerId,
} from '@/types/PlannerTypes'

const PLANNERS_BASE = '/api/planners'

/**
 * Planner API methods for server synchronization
 * All methods validate responses with Zod schemas
 */
export const plannerApi = {
  /**
   * Create a new planner on the server
   *
   * @param request - Create planner request payload
   * @returns Created planner with server-assigned ID and timestamps
   */
  async create(request: CreatePlannerRequest): Promise<ServerPlannerResponse> {
    const data = await ApiClient.post(PLANNERS_BASE, request)
    return ServerPlannerResponseSchema.parse(data)
  },

  /**
   * List all planners for the current user with pagination
   *
   * @param page - Page number (0-indexed)
   * @param size - Number of items per page
   * @returns Array of planner summaries (without full content)
   */
  async list(page = 0, size = 100): Promise<ServerPlannerSummary[]> {
    const data = await ApiClient.get<{ content: unknown[] }>(
      `${PLANNERS_BASE}?page=${page}&size=${size}`
    )
    return ServerPlannerSummaryArraySchema.parse(data.content)
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
   * Update an existing planner
   * Requires syncVersion for optimistic locking
   *
   * @param id - Planner UUID
   * @param request - Update request with syncVersion
   * @returns Updated planner with new syncVersion
   * @throws ApiConflictError if syncVersion doesn't match (HTTP 409)
   */
  async update(
    id: PlannerId | string,
    request: UpdatePlannerRequest
  ): Promise<ServerPlannerResponse> {
    const data = await ApiClient.put(`${PLANNERS_BASE}/${id}`, request)
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
   * Create an EventSource for real-time planner updates
   * Used for multi-device sync notifications
   *
   * @returns EventSource connected to planner events endpoint
   *
   * @example
   * const eventSource = plannerApi.createEventsConnection()
   * eventSource.onmessage = (event) => {
   *   const data = PlannerSseEventSchema.parse(JSON.parse(event.data))
   *   // Handle update...
   * }
   * eventSource.onerror = () => eventSource.close()
   */
  createEventsConnection(): EventSource {
    return ApiClient.createEventSource(`${PLANNERS_BASE}/events`)
  },
}
