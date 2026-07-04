import { describe, it, expect, vi } from 'vitest'
import { keepPreviousData } from '@tanstack/react-query'
import { z } from 'zod'

import { createStaticDataQueryOptions } from '../queryOptions'
import { STATIC_DATA_STALE_TIME } from '../constants'

const TestSchema = z.object({ name: z.string() })
const QUERY_KEY = ['test', 'list', 'spec'] as const

function createImporter(data: unknown) {
  return vi.fn(() => Promise.resolve({ default: data }))
}

function invokeQueryFn<T>(options: { queryFn?: unknown }): Promise<T> {
  const queryFn = options.queryFn as (context: unknown) => Promise<T>
  return queryFn({
    queryKey: QUERY_KEY,
    signal: new AbortController().signal,
    meta: undefined,
  })
}

describe('createStaticDataQueryOptions', () => {
  it('passes the queryKey through unchanged', () => {
    const options = createStaticDataQueryOptions(
      QUERY_KEY,
      createImporter({ name: 'Dante' }),
      TestSchema,
      'test data',
    )

    expect(options.queryKey).toEqual(['test', 'list', 'spec'])
  })

  it('does not call the importer until queryFn runs', async () => {
    const importer = createImporter({ name: 'Dante' })
    const options = createStaticDataQueryOptions(QUERY_KEY, importer, TestSchema, 'test data')

    expect(importer).not.toHaveBeenCalled()

    await invokeQueryFn(options)

    expect(importer).toHaveBeenCalledTimes(1)
  })

  it('returns the schema-validated module default', async () => {
    const importer = createImporter({ name: 'Dante', extra: 'stripped by z.object' })
    const options = createStaticDataQueryOptions(QUERY_KEY, importer, TestSchema, 'test data')

    const data = await invokeQueryFn(options)

    expect(data).toEqual({ name: 'Dante' })
  })

  it('rejects with the "[context] Validation failed: …" message on invalid data', async () => {
    const importer = createImporter({ name: 12345 })
    const options = createStaticDataQueryOptions(QUERY_KEY, importer, TestSchema, 'test data')

    await expect(invokeQueryFn(options)).rejects.toThrowError(/^\[test data\] Validation failed: /)
  })

  it('defaults staleTime to STATIC_DATA_STALE_TIME (7 days)', () => {
    const options = createStaticDataQueryOptions(
      QUERY_KEY,
      createImporter({ name: 'Dante' }),
      TestSchema,
      'test data',
    )

    expect(options.staleTime).toBe(STATIC_DATA_STALE_TIME)
    expect(STATIC_DATA_STALE_TIME).toBe(7 * 24 * 60 * 60 * 1000)
  })

  it('maps keepPrevious: true to placeholderData: keepPreviousData', () => {
    const options = createStaticDataQueryOptions(
      QUERY_KEY,
      createImporter({ name: 'Dante' }),
      TestSchema,
      'test data',
      { keepPrevious: true },
    )

    expect(options.placeholderData).toBe(keepPreviousData)
  })

  it('leaves placeholderData unset when keepPrevious is omitted', () => {
    const options = createStaticDataQueryOptions(
      QUERY_KEY,
      createImporter({ name: 'Dante' }),
      TestSchema,
      'test data',
    )

    expect(options.placeholderData).toBeUndefined()
  })
})
