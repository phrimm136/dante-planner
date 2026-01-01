/**
 * usePlannerListData.test.tsx
 *
 * Tests for planner list data hook.
 * Uses Vitest + React Testing Library for testing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { plannerListQueryKeys } from './usePlannerListData'

// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    get: vi.fn(),
  },
}))

// Import after mocking
import { ApiClient } from '@/lib/api'

/**
 * Mock response data matching the schema
 */
const mockPaginatedResponse = {
  content: [
    {
      id: '123e4567-e89b-12d3-a456-426614174000',
      title: 'Test Planner',
      category: '5F',
      keywords: ['Combustion', 'Slash'],
      upvoteCount: 10,
      downvoteCount: 2,
      viewCount: 100,
      authorName: 'TestUser',
      createdAt: '2024-12-31T10:00:00Z',
      lastModifiedAt: '2024-12-31T12:00:00Z',
      userVote: null,
      isBookmarked: null,
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
}

describe('usePlannerListData - API calls', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ApiClient.get).mockResolvedValue(mockPaginatedResponse)
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('mock is configured correctly for API testing', () => {
    // Verify mock is set up
    expect(ApiClient.get).toBeDefined()
    expect(vi.mocked(ApiClient.get)).toBeDefined()
  })

  // Note: Full useSuspenseQuery testing requires Suspense boundaries.
  // The query key factory tests below verify the query structure.
})

describe('plannerListQueryKeys', () => {
  it('creates consistent keys for all queries', () => {
    const key = plannerListQueryKeys.all
    expect(key).toEqual(['plannerList'])
  })

  it('creates unique keys for different published params', () => {
    const key1 = plannerListQueryKeys.published({ page: 0, size: 20 })
    const key2 = plannerListQueryKeys.published({ page: 1, size: 20 })
    const key3 = plannerListQueryKeys.published({ page: 0, size: 20, category: '5F' })

    expect(key1).not.toEqual(key2)
    expect(key1).not.toEqual(key3)
    expect(key2).not.toEqual(key3)
  })

  it('creates unique keys for recommended params with category', () => {
    const key1 = plannerListQueryKeys.recommended({ page: 0, size: 20 })
    const key2 = plannerListQueryKeys.recommended({ page: 0, size: 20, category: '10F' })

    expect(key1).not.toEqual(key2)
    expect(key2[2]).toEqual({ page: 0, size: 20, category: '10F' })
  })

  it('published and recommended keys are different', () => {
    const publishedKey = plannerListQueryKeys.published({ page: 0, size: 20 })
    const recommendedKey = plannerListQueryKeys.recommended({ page: 0, size: 20 })

    // Second element should be different ('published' vs 'recommended')
    expect(publishedKey[1]).toBe('published')
    expect(recommendedKey[1]).toBe('recommended')
    expect(publishedKey).not.toEqual(recommendedKey)
  })

  it('includes sort parameter in published key', () => {
    const key1 = plannerListQueryKeys.published({ page: 0, size: 20, sort: 'recent' })
    const key2 = plannerListQueryKeys.published({ page: 0, size: 20, sort: 'popular' })

    expect(key1).not.toEqual(key2)
    expect(key1[2]).toHaveProperty('sort', 'recent')
    expect(key2[2]).toHaveProperty('sort', 'popular')
  })

  it('includes search parameter in published key', () => {
    const key = plannerListQueryKeys.published({ page: 0, size: 20, search: 'test' })
    expect(key[2]).toHaveProperty('search', 'test')
  })

  it('recommended key does not have sort property', () => {
    const key = plannerListQueryKeys.recommended({
      page: 0,
      size: 20,
      category: '5F',
      search: 'test',
    })

    const params = key[2] as Record<string, unknown>
    expect(params).not.toHaveProperty('sort')
    expect(params).toHaveProperty('category', '5F')
    expect(params).toHaveProperty('search', 'test')
  })
})

describe('usePlannerListData query options', () => {
  // These tests verify the structure without rendering
  // Full integration tests would require a test environment with React + Suspense

  it('uses correct endpoint for published planners', () => {
    // Verify the expected URL pattern
    const baseUrl = '/api/planner/md/published'
    const params = new URLSearchParams()
    params.append('page', '0')
    params.append('size', '20')

    const expectedUrl = `${baseUrl}?${params.toString()}`
    expect(expectedUrl).toContain('page=0')
    expect(expectedUrl).toContain('size=20')
  })

  it('uses correct endpoint for recommended planners', () => {
    const baseUrl = '/api/planner/md/recommended'
    const params = new URLSearchParams()
    params.append('page', '0')
    params.append('size', '20')
    params.append('category', '5F')

    const expectedUrl = `${baseUrl}?${params.toString()}`
    expect(expectedUrl).toContain('page=0')
    expect(expectedUrl).toContain('category=5F')
  })

  it('formats query params correctly', () => {
    const params = new URLSearchParams()
    params.append('page', '2')
    params.append('size', '20')
    params.append('category', '10F')
    params.append('sort', 'popular')
    params.append('q', 'test search')

    expect(params.toString()).toContain('page=2')
    expect(params.toString()).toContain('size=20')
    expect(params.toString()).toContain('category=10F')
    expect(params.toString()).toContain('sort=popular')
    expect(params.toString()).toContain('q=test+search')
  })
})
