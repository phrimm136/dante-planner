import { describe, expect, it } from 'vitest'
import { PaginatedPlannersSchema, PublishedPlannerDetailSchema } from '../PlannerListSchemas'

const listedWithoutCreatedAt = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  title: 'Test Planner',
  plannerType: 'MIRROR_DUNGEON',
  category: '5F',
  upvotes: 0,
  viewCount: 0,
  firstPublishedAt: '2026-01-01T00:00:00Z',
  hasUpvoted: false,
  commentCount: 0,
}

const createdAt = '2026-01-01T00:00:00Z'

const detailWithoutCreatedAt = {
  ...listedWithoutCreatedAt,
  lastModifiedAt: createdAt,
  content: '{}',
  schemaVersion: 2,
  contentVersion: 6,
  status: 'saved',
  syncVersion: 1,
  isSubscribed: false,
  hasReported: false,
  ownerNotificationsEnabled: false,
}

function page(content: unknown[]) {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}

describe('PaginatedPlannersSchema', () => {
  it('accepts a page holding a row whose createdAt is absent or null', () => {
    const rows = [
      listedWithoutCreatedAt,
      { ...listedWithoutCreatedAt, createdAt: null },
      { ...listedWithoutCreatedAt, createdAt },
    ]

    const parsed = PaginatedPlannersSchema.parse(page(rows))

    expect(parsed.content.map((row) => row.createdAt)).toEqual([undefined, null, createdAt])
  })
})

describe('PublishedPlannerDetailSchema', () => {
  it('requires createdAt, which the saveable planner built from the detail needs', () => {
    expect(
      PublishedPlannerDetailSchema.safeParse({ ...detailWithoutCreatedAt, createdAt }).success,
    ).toBe(true)
    expect(PublishedPlannerDetailSchema.safeParse(detailWithoutCreatedAt).success).toBe(false)
  })
})
