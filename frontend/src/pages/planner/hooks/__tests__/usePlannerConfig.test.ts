/**
 * usePlannerConfig.test.ts
 *
 * Tests for planner config hook.
 * Validates that the hook returns the static config from constants
 * and that the config satisfies the schema.
 */

import { describe, it, expect } from 'vitest'
import { renderHook } from '@testing-library/react'
import { usePlannerConfig } from '../usePlannerConfig'
import { PLANNER_CONFIG } from '@/lib/constants'
import { PlannerConfigSchema } from '../../schemas/PlannerSchemas'

describe('usePlannerConfig', () => {
  it('returns PLANNER_CONFIG from constants', () => {
    const { result } = renderHook(() => usePlannerConfig())
    expect(result.current).toBe(PLANNER_CONFIG)
  })

  it('returns config matching PlannerConfigSchema', () => {
    const { result } = renderHook(() => usePlannerConfig())
    const parsed = PlannerConfigSchema.safeParse(result.current)
    expect(parsed.success).toBe(true)
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

describe('PlannerConfigSchema', () => {
  it('validates correct config response', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 2,
      mdCurrentVersion: 7,
      mdAvailableVersions: [6, 7],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(true)
  })

  it('rejects config without schemaVersion', () => {
    const result = PlannerConfigSchema.safeParse({
      mdCurrentVersion: 6,
      mdAvailableVersions: [6],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects config without mdCurrentVersion', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdAvailableVersions: [6],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects config without mdAvailableVersions', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6,
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects config without rrAvailableVersions', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6,
      mdAvailableVersions: [6],
    })
    expect(result.success).toBe(false)
  })

  it('rejects non-positive schemaVersion', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 0,
      mdCurrentVersion: 6,
      mdAvailableVersions: [6],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects non-integer mdCurrentVersion', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6.5,
      mdAvailableVersions: [6],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects empty mdAvailableVersions array', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6,
      mdAvailableVersions: [],
      rrAvailableVersions: [1, 5],
    })
    expect(result.success).toBe(false)
  })

  it('rejects empty rrAvailableVersions array', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6,
      mdAvailableVersions: [6],
      rrAvailableVersions: [],
    })
    expect(result.success).toBe(false)
  })

  it('rejects extra unknown fields (strict schema)', () => {
    const result = PlannerConfigSchema.safeParse({
      schemaVersion: 1,
      mdCurrentVersion: 6,
      mdAvailableVersions: [6],
      rrAvailableVersions: [1, 5],
      unknownField: 'should fail',
    })
    expect(result.success).toBe(false)
  })
})
