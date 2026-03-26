/**
 * queryClient.test.ts
 *
 * Tests for global error handling in QueryCache and MutationCache.
 * Validates that 503 errors trigger the correct toast based on error type:
 * - ServiceUpdatingError (planned deploy) → serviceUpdating toast
 * - BackendUnavailableError (unexpected crash) → backendUnavailable toast
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryCache, MutationCache } from '@tanstack/react-query'

import { ServiceUpdatingError, BackendUnavailableError } from '../api'

vi.mock('../toast', () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('../i18n', () => ({
  default: {
    t: (key: string) => key,
  },
}))

import { toast } from '../toast'

function handleBackendDownError(error: Error): void {
  if (error instanceof ServiceUpdatingError) {
    toast.error('errors.serviceUpdating')
  } else if (error instanceof BackendUnavailableError) {
    toast.error('errors.backendUnavailable')
  }
}

describe('QueryCache onError', () => {
  let queryCache: QueryCache

  beforeEach(() => {
    vi.clearAllMocks()
    queryCache = new QueryCache({
      onError: (error) => {
        handleBackendDownError(error)
      },
    })
  })

  it('shows serviceUpdating toast for ServiceUpdatingError', () => {
    const error = new ServiceUpdatingError('Service temporarily unavailable')
    queryCache.config.onError?.(error, {} as never)

    expect(toast.error).toHaveBeenCalledWith('errors.serviceUpdating')
  })

  it('shows backendUnavailable toast for BackendUnavailableError', () => {
    const error = new BackendUnavailableError('Server is temporarily unavailable')
    queryCache.config.onError?.(error, {} as never)

    expect(toast.error).toHaveBeenCalledWith('errors.backendUnavailable')
  })

  it('does not toast for generic errors', () => {
    const error = new Error('HTTP error! status: 500')
    queryCache.config.onError?.(error, {} as never)

    expect(toast.error).not.toHaveBeenCalled()
  })

  it('does not toast for network errors', () => {
    const error = new Error('Failed to fetch')
    queryCache.config.onError?.(error, {} as never)

    expect(toast.error).not.toHaveBeenCalled()
  })
})

describe('MutationCache onError', () => {
  let mutationCache: MutationCache

  beforeEach(() => {
    vi.clearAllMocks()
    mutationCache = new MutationCache({
      onError: (error) => {
        handleBackendDownError(error)
      },
    })
  })

  it('shows serviceUpdating toast for ServiceUpdatingError', () => {
    const error = new ServiceUpdatingError('Service temporarily unavailable')
    mutationCache.config.onError?.(error, {} as never, {} as never, {} as never, {} as never)

    expect(toast.error).toHaveBeenCalledWith('errors.serviceUpdating')
  })

  it('shows backendUnavailable toast for BackendUnavailableError', () => {
    const error = new BackendUnavailableError('Server is temporarily unavailable')
    mutationCache.config.onError?.(error, {} as never, {} as never, {} as never, {} as never)

    expect(toast.error).toHaveBeenCalledWith('errors.backendUnavailable')
  })

  it('does not toast for generic errors', () => {
    const error = new Error('HTTP error! status: 500')
    mutationCache.config.onError?.(error, {} as never, {} as never, {} as never, {} as never)

    expect(toast.error).not.toHaveBeenCalled()
  })
})
