import { describe, it, expect } from 'vitest'

import { decidePublishAction } from '../plannerPublishPolicy'

import { contentPath, field, key } from '../validation/contentPath'
import type { PlannerValidationError } from '../validation/plannerValidationErrors'

const missingTitle: PlannerValidationError = {
  code: 'MISSING_TITLE',
  path: contentPath(field('title')),
}

const missingIdentity: PlannerValidationError = {
  code: 'MISSING_IDENTITY',
  path: contentPath(field('equipment'), key('01'), field('identity')),
}

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
