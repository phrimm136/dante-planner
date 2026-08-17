import { describe, it, expect } from 'vitest'

import { decidePublishAction } from '../plannerPublishPolicy'

import type { PlannerValidationError } from '../plannerValidationErrors'

const missingTitle: PlannerValidationError = {
  code: 'MISSING_TITLE',
  message: 'Title is required for publishing',
  field: 'title',
} as PlannerValidationError

const missingIdentity: PlannerValidationError = {
  code: 'EQUIPMENT_MISSING_IDENTITY',
  message: 'Identity missing',
} as PlannerValidationError

describe('decidePublishAction', () => {
  it.each([true, false, null, undefined])(
    'unpublishes without validating, whatever sync says (%s)',
    (syncEnabled) => {
      expect(
        decidePublishAction({
          isPublished: true,
          validationErrors: [missingTitle],
          syncEnabled,
        }),
      ).toEqual({ kind: 'unpublish' })
    },
  )

  it('refuses to publish on the first validation error', () => {
    expect(
      decidePublishAction({
        isPublished: false,
        validationErrors: [missingTitle, missingIdentity],
        syncEnabled: true,
      }),
    ).toEqual({ kind: 'invalid', error: missingTitle })
  })

  it.each([
    [false, 'warnSyncDisabled'],
    [null, 'warnSyncDisabled'],
    [undefined, 'warnSyncDisabled'],
    [true, 'uploadThenPublish'],
  ] as const)('sync %s on a valid planner → %s', (syncEnabled, kind) => {
    expect(decidePublishAction({ isPublished: false, validationErrors: [], syncEnabled })).toEqual({
      kind,
    })
  })

  it.each([null, undefined])('treats a %s published flag as unpublished', (isPublished) => {
    expect(decidePublishAction({ isPublished, validationErrors: [], syncEnabled: true })).toEqual({
      kind: 'uploadThenPublish',
    })
  })
})
