import { describe, it, expect } from 'vitest'
import { classifyRouteError, markChunkLoadError, routeErrorLabel } from '../routeErrorCode'

describe('classifyRouteError', () => {
  it('classifies a stamped error as CHUNK_LOAD', () => {
    const error = new TypeError('Failed to fetch dynamically imported module')
    markChunkLoadError(error)

    expect(classifyRouteError(error)).toBe('CHUNK_LOAD')
    expect(routeErrorLabel(error)).toBe('CHUNK_LOAD')
  })

  it('classifies a stamped plain object as CHUNK_LOAD', () => {
    const payload = {}
    markChunkLoadError(payload)

    expect(classifyRouteError(payload)).toBe('CHUNK_LOAD')
  })

  it('labels an unstamped Error with its name', () => {
    const error = new TypeError('x')

    expect(classifyRouteError(error)).toBe('UNKNOWN')
    expect(routeErrorLabel(error)).toBe('UNKNOWN · TypeError')
  })

  it('labels a non-Error value without a name', () => {
    expect(classifyRouteError('str')).toBe('UNKNOWN')
    expect(routeErrorLabel('str')).toBe('UNKNOWN')
  })

  it('classifies null as UNKNOWN', () => {
    expect(classifyRouteError(null)).toBe('UNKNOWN')
    expect(routeErrorLabel(null)).toBe('UNKNOWN')
  })

  it('ignores a primitive passed to markChunkLoadError', () => {
    expect(() => markChunkLoadError('str')).not.toThrow()
    expect(() => markChunkLoadError(null)).not.toThrow()
    expect(() => markChunkLoadError(undefined)).not.toThrow()
    expect(classifyRouteError('str')).toBe('UNKNOWN')
  })
})
