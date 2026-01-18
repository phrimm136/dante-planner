/**
 * CommentSchemas.test.ts
 *
 * Tests for comment system Zod schema validation.
 * Validates CommentNodeSchema, CommentTreeSchema, and action response schemas.
 */

import { describe, it, expect } from 'vitest'

import {
  CommentNodeSchema,
  CommentTreeSchema,
  CommentVoteResponseSchema,
  CommentReportResponseSchema,
  CommentReportReasonSchema,
} from './CommentSchemas'

// ============================================================================
// Test Fixtures
// ============================================================================

const validCommentNode = {
  id: '123e4567-e89b-12d3-a456-426614174000',
  content: 'Test comment',
  authorAssoc: 'W_CORP',
  authorSuffix: 'ABC12',
  isAuthor: false,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: null,
  isUpdated: false,
  isDeleted: false,
  upvoteCount: 5,
  hasUpvoted: true,
  authorNotificationsEnabled: true,
  replies: [],
}

// ============================================================================
// CommentNodeSchema Tests
// ============================================================================

describe('CommentNodeSchema', () => {
  it('should validate a valid comment node', () => {
    const result = CommentNodeSchema.safeParse(validCommentNode)
    expect(result.success).toBe(true)
  })

  it('should validate deleted comment with empty content', () => {
    const deletedComment = {
      ...validCommentNode,
      content: '',
      isDeleted: true,
    }
    const result = CommentNodeSchema.safeParse(deletedComment)
    expect(result.success).toBe(true)
  })

  it('should validate updated comment', () => {
    const updatedComment = {
      ...validCommentNode,
      updatedAt: '2024-01-02T00:00:00Z',
      isUpdated: true,
    }
    const result = CommentNodeSchema.safeParse(updatedComment)
    expect(result.success).toBe(true)
  })

  it('should validate comment with nested replies', () => {
    const withReplies = {
      ...validCommentNode,
      replies: [
        { ...validCommentNode, id: '223e4567-e89b-12d3-a456-426614174001' },
        { ...validCommentNode, id: '323e4567-e89b-12d3-a456-426614174002' },
      ],
    }
    const result = CommentNodeSchema.safeParse(withReplies)
    expect(result.success).toBe(true)
  })

  it('should validate deeply nested replies', () => {
    const deepNested = {
      ...validCommentNode,
      replies: [
        {
          ...validCommentNode,
          id: '223e4567-e89b-12d3-a456-426614174001',
          replies: [
            {
              ...validCommentNode,
              id: '323e4567-e89b-12d3-a456-426614174002',
            },
          ],
        },
      ],
    }
    const result = CommentNodeSchema.safeParse(deepNested)
    expect(result.success).toBe(true)
  })

  it('should reject invalid id (not UUID)', () => {
    const invalidComment = { ...validCommentNode, id: 'not-a-uuid' }
    const result = CommentNodeSchema.safeParse(invalidComment)
    expect(result.success).toBe(false)
  })

  it('should reject negative upvoteCount', () => {
    const invalidComment = { ...validCommentNode, upvoteCount: -1 }
    const result = CommentNodeSchema.safeParse(invalidComment)
    expect(result.success).toBe(false)
  })

  it('should reject missing required fields', () => {
    const { content, ...missingContent } = validCommentNode
    const result = CommentNodeSchema.safeParse(missingContent)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// CommentTreeSchema Tests
// ============================================================================

describe('CommentTreeSchema', () => {
  it('should validate empty array', () => {
    const result = CommentTreeSchema.safeParse([])
    expect(result.success).toBe(true)
  })

  it('should validate array with single comment', () => {
    const result = CommentTreeSchema.safeParse([validCommentNode])
    expect(result.success).toBe(true)
  })

  it('should validate array with multiple top-level comments', () => {
    const comments = [
      validCommentNode,
      { ...validCommentNode, id: '223e4567-e89b-12d3-a456-426614174001' },
      { ...validCommentNode, id: '323e4567-e89b-12d3-a456-426614174002' },
    ]
    const result = CommentTreeSchema.safeParse(comments)
    expect(result.success).toBe(true)
  })

  it('should reject array with invalid comment', () => {
    const comments = [
      validCommentNode,
      { ...validCommentNode, id: 'invalid' },
    ]
    const result = CommentTreeSchema.safeParse(comments)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// CommentVoteResponseSchema Tests
// ============================================================================

describe('CommentVoteResponseSchema', () => {
  it('should validate vote response', () => {
    const response = {
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      upvoteCount: 10,
      hasUpvoted: true,
    }
    const result = CommentVoteResponseSchema.safeParse(response)
    expect(result.success).toBe(true)
  })

  it('should validate zero upvoteCount', () => {
    const response = {
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      upvoteCount: 0,
      hasUpvoted: false,
    }
    const result = CommentVoteResponseSchema.safeParse(response)
    expect(result.success).toBe(true)
  })

  it('should reject extra fields (strict)', () => {
    const response = {
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      upvoteCount: 10,
      hasUpvoted: true,
      extraField: 'not allowed',
    }
    const result = CommentVoteResponseSchema.safeParse(response)
    expect(result.success).toBe(false)
  })

  it('should reject negative upvoteCount', () => {
    const response = {
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      upvoteCount: -1,
      hasUpvoted: true,
    }
    const result = CommentVoteResponseSchema.safeParse(response)
    expect(result.success).toBe(false)
  })

  it('should reject invalid commentId (not UUID)', () => {
    const response = {
      commentId: 'not-a-uuid',
      upvoteCount: 10,
      hasUpvoted: true,
    }
    const result = CommentVoteResponseSchema.safeParse(response)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// CommentReportResponseSchema Tests
// ============================================================================

describe('CommentReportResponseSchema', () => {
  it('should validate report response', () => {
    const response = {
      id: 1,
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      reason: 'SPAM',
      createdAt: '2024-01-01T00:00:00Z',
    }
    const result = CommentReportResponseSchema.safeParse(response)
    expect(result.success).toBe(true)
  })

  it('should reject extra fields (strict)', () => {
    const response = {
      id: 1,
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      reason: 'SPAM',
      createdAt: '2024-01-01T00:00:00Z',
      extraField: 'not allowed',
    }
    const result = CommentReportResponseSchema.safeParse(response)
    expect(result.success).toBe(false)
  })

  it('should reject missing createdAt', () => {
    const response = {
      id: 1,
      commentId: '123e4567-e89b-12d3-a456-426614174000',
      reason: 'SPAM',
    }
    const result = CommentReportResponseSchema.safeParse(response)
    expect(result.success).toBe(false)
  })
})

// ============================================================================
// CommentReportReasonSchema Tests
// ============================================================================

describe('CommentReportReasonSchema', () => {
  it('should validate all valid reasons', () => {
    const validReasons = ['SPAM', 'HARASSMENT', 'OFF_TOPIC', 'OTHER'] as const
    for (const reason of validReasons) {
      const result = CommentReportReasonSchema.safeParse(reason)
      expect(result.success).toBe(true)
    }
  })

  it('should reject invalid reason', () => {
    const result = CommentReportReasonSchema.safeParse('INVALID_REASON')
    expect(result.success).toBe(false)
  })

  it('should reject lowercase reason', () => {
    const result = CommentReportReasonSchema.safeParse('spam')
    expect(result.success).toBe(false)
  })
})
