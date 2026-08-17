import { describe, it, expect } from 'vitest'

import { ModerationActionSchema, ModerationActionTypeSchema } from '../ModeratorSchemas'

/** Transcribed from backend `ModerationAction.ActionType`, in declaration order. */
const BACKEND_ACTION_TYPES = [
  'BAN',
  'UNBAN',
  'TIMEOUT',
  'CLEAR_TIMEOUT',
  'PROMOTE',
  'DEMOTE',
  'DELETE_PLANNER',
  'DELETE_COMMENT',
  'UNPUBLISH_PLANNER',
  'HIDE_FROM_RECOMMENDED',
  'UNHIDE_FROM_RECOMMENDED',
]

function auditRow(actionType: string) {
  return {
    actionType,
    targetType: 'PLANNER',
    targetUuid: '11111111-1111-4111-8111-111111111111',
    reason: 'spam',
    durationMinutes: 0,
    createdAt: '2026-03-04T05:06:07Z',
    actorUsernameEpithet: 'NAIVE',
    actorUsernameSuffix: 'ab12c',
  }
}

describe('ModerationActionTypeSchema', () => {
  it('names exactly the backend action types', () => {
    const byValue = (a: string, b: string) => a.localeCompare(b)
    expect([...ModerationActionTypeSchema.options].sort(byValue)).toEqual(
      [...BACKEND_ACTION_TYPES].sort(byValue),
    )
  })
})

describe('ModerationActionSchema', () => {
  it.each(BACKEND_ACTION_TYPES)('parses an audit row for %s', (actionType) => {
    expect(ModerationActionSchema.safeParse(auditRow(actionType)).success).toBe(true)
  })

  it('rejects an action type the backend never records', () => {
    expect(ModerationActionSchema.safeParse(auditRow('SHADOWBAN')).success).toBe(false)
  })
})
