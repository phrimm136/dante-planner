/**
 * usePlannerConfig.test.ts
 *
 * Tests for planner config hook.
 * Validates that the hook returns the config parsed from plannerVersions.json
 * and that the file schema rejects malformed version lists.
 */

import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import { usePlannerConfig } from '../usePlannerConfig'
import { PLANNER_CONFIG, PlannerVersionsFileSchema } from '@/lib/constants'

describe('usePlannerConfig', () => {
  it('returns PLANNER_CONFIG from constants', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current).toBe(PLANNER_CONFIG)
  })

  it('returns the versions parsed from plannerVersions.json, current season last', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current).toEqual({
      schemaVersion: 2,
      mdAvailableVersions: [6, 7],
      mdCurrentVersion: 7,
      rrAvailableVersions: [1, 5],
    })
  })

  it('has positive schemaVersion', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current.schemaVersion).toBeGreaterThan(0)
  })

  it('has positive mdCurrentVersion', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current.mdCurrentVersion).toBeGreaterThan(0)
  })

  it('has non-empty mdAvailableVersions', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current.mdAvailableVersions.length).toBeGreaterThan(0)
  })

  it('has non-empty rrAvailableVersions', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current.rrAvailableVersions.length).toBeGreaterThan(0)
  })

  it('includes mdCurrentVersion in mdAvailableVersions', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current.mdAvailableVersions).toContain(result.current.mdCurrentVersion)
  })
})

const VALID_FILE = {
  schemaVersion: 2,
  mdAvailableVersions: [6, 7],
  rrAvailableVersions: [1, 5],
}

describe('PlannerVersionsFileSchema', () => {
  it('validates the versions file', () => {
    expect(PlannerVersionsFileSchema.safeParse(VALID_FILE).success).toBe(true)
  })

  it.each(['schemaVersion', 'mdAvailableVersions', 'rrAvailableVersions'] as const)(
    'rejects a file without %s',
    (field) => {
      const { [field]: _omitted, ...rest } = VALID_FILE
      expect(PlannerVersionsFileSchema.safeParse(rest).success).toBe(false)
    },
  )

  it('rejects non-positive schemaVersion', () => {
    const result = PlannerVersionsFileSchema.safeParse({ ...VALID_FILE, schemaVersion: 0 })
    expect(result.success).toBe(false)
  })

  it('rejects a non-integer season', () => {
    const result = PlannerVersionsFileSchema.safeParse({
      ...VALID_FILE,
      mdAvailableVersions: [6, 6.5],
    })
    expect(result.success).toBe(false)
  })

  it.each([
    { name: 'descending mdAvailableVersions', patch: { mdAvailableVersions: [7, 6] } },
    { name: 'repeated mdAvailableVersions', patch: { mdAvailableVersions: [6, 6] } },
    { name: 'empty mdAvailableVersions', patch: { mdAvailableVersions: [] } },
    { name: 'descending rrAvailableVersions', patch: { rrAvailableVersions: [5, 1] } },
    { name: 'empty rrAvailableVersions', patch: { rrAvailableVersions: [] } },
  ])('rejects $name', ({ patch }) => {
    expect(PlannerVersionsFileSchema.safeParse({ ...VALID_FILE, ...patch }).success).toBe(false)
  })

  it('rejects a file carrying mdCurrentVersion (strict schema)', () => {
    const result = PlannerVersionsFileSchema.safeParse({ ...VALID_FILE, mdCurrentVersion: 7 })
    expect(result.success).toBe(false)
  })

  it('rejects extra unknown fields (strict schema)', () => {
    const result = PlannerVersionsFileSchema.safeParse({
      ...VALID_FILE,
      unknownField: 'should fail',
    })
    expect(result.success).toBe(false)
  })
})
