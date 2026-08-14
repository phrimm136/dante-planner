import { describe, it, expect } from 'vitest'

import { AuthTemporarilyUnavailableError, BackendUnavailableError, BannedError, ConflictError, ForbiddenError, NotFoundError, RateLimitError, RetryableUnavailableError, ServiceUpdatingError, TimedOutError, ValidationError, WriteTemporarilyUnavailableError } from '@/lib/apiErrors'

import { classifyAppError, validationAppError } from '../apiErrorClassifier'
import type { AppError } from '../apiErrorClassifier'

describe('classifyAppError', () => {
  // One row per error class `lib/api.ts` throws, so a class added there without a
  // classification shows up as a missing row rather than as a silent `unknown`.
  const apiClasses: { name: string; thrown: unknown; expected: AppError }[] = [
    {
      name: 'a sync conflict',
      thrown: new ConflictError('SYNC_CONFLICT', 'conflict', 7),
      expected: { kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 7 },
    },
    {
      name: 'a rate limit',
      thrown: new RateLimitError('too many'),
      expected: { kind: 'rateLimit' },
    },
    {
      name: 'a backend validation failure',
      thrown: new ValidationError('TITLE_TOO_LONG', 'invalid'),
      expected: { kind: 'validation', key: 'common:errors.validation.message' },
    },
    {
      name: 'a missing resource',
      thrown: new NotFoundError('Resource not found'),
      expected: { kind: 'notFound' },
    },
    {
      name: 'a ban',
      thrown: new BannedError('banned'),
      expected: { kind: 'restricted', reason: 'banned' },
    },
    {
      name: 'a timeout',
      thrown: new TimedOutError('timed out'),
      expected: { kind: 'restricted', reason: 'timedOut' },
    },
    {
      name: 'a forbidden action',
      thrown: new ForbiddenError('PLANNER_FORBIDDEN', 'nope'),
      expected: { kind: 'forbidden', code: 'PLANNER_FORBIDDEN' },
    },
    {
      name: 'a planned deploy',
      thrown: new ServiceUpdatingError('updating'),
      expected: { kind: 'unavailable', scope: 'service' },
    },
    {
      name: 'an unreachable backend',
      thrown: new BackendUnavailableError('down'),
      expected: { kind: 'unavailable', scope: 'backend' },
    },
    {
      name: 'a write held during failover',
      thrown: new WriteTemporarilyUnavailableError('paused'),
      expected: { kind: 'unavailable', scope: 'write' },
    },
    {
      name: 'auth held during failover',
      thrown: new AuthTemporarilyUnavailableError('paused'),
      expected: { kind: 'unavailable', scope: 'auth' },
    },
    {
      name: 'a failure the backend says to retry',
      thrown: new RetryableUnavailableError('deadlock'),
      expected: { kind: 'retryable' },
    },
  ]

  it.each(apiClasses)('maps $name', ({ thrown, expected }) => {
    expect(classifyAppError(thrown)).toEqual(expected)
  })

  it('covers every error class the api layer throws', () => {
    expect(apiClasses).toHaveLength(12)
  })

  it('maps an exhausted storage quota', () => {
    expect(
      classifyAppError(new DOMException('The quota has been exceeded.', 'QuotaExceededError')),
    ).toEqual({ kind: 'quota' })
  })

  it('leaves a DOMException that is not about quota unknown', () => {
    expect(classifyAppError(new DOMException('closed', 'InvalidStateError'))).toEqual({
      kind: 'unknown',
    })
  })

  const unrecognized: { name: string; thrown: unknown }[] = [
    { name: 'a bare error', thrown: new Error('boom') },
    { name: 'a thrown string', thrown: 'boom' },
    { name: 'a thrown null', thrown: null },
    { name: 'a thrown undefined', thrown: undefined },
    { name: 'a thrown object', thrown: { code: 'SYNC_CONFLICT' } },
  ]

  it.each(unrecognized)('classifies $name as unknown', ({ thrown }) => {
    expect(classifyAppError(thrown)).toEqual({ kind: 'unknown' })
  })

  it('carries a null server version out of a conflict that reported none', () => {
    expect(classifyAppError(new ConflictError('CONCURRENT_WRITE', 'conflict', null))).toEqual({
      kind: 'conflict',
      code: 'CONCURRENT_WRITE',
      serverVersion: null,
    })
  })

  it('does not read the message of an error that only mentions a quota', () => {
    expect(classifyAppError(new Error('storage quota reached'))).toEqual({ kind: 'unknown' })
  })
})

describe('validationAppError', () => {
  it('carries the validator key and params into the app error vocabulary', () => {
    expect(
      validationAppError({
        key: 'planner:pages.plannerMD.publish.missingTitle',
        params: { gifts: 'A, B' },
      }),
    ).toEqual({
      kind: 'validation',
      key: 'planner:pages.plannerMD.publish.missingTitle',
      params: { gifts: 'A, B' },
    })
  })

  it('leaves params undefined when the validator reported none', () => {
    expect(validationAppError({ key: 'planner:a.b.c' })).toEqual({
      kind: 'validation',
      key: 'planner:a.b.c',
      params: undefined,
    })
  })
})
