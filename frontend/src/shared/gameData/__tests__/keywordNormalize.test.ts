/**
 * keywordNormalize.test.ts
 *
 * Unit tests for the keyword migration converter. Verifies renamed keyword ids are
 * remapped to current ids, unknown ids are preserved (for the strict tier to reject),
 * and non-array input is handled defensively.
 */

import { describe, it, expect } from 'vitest'
import { migrateKeywords } from '../keywordNormalize'

describe('migrateKeywords', () => {
  it('remaps legacy keyword ids to their current ids', () => {
    expect(migrateKeywords(['AccelBullet', 'ChargeLoad'])).toEqual([
      '9828',
      'EmergencyChargeForceField',
    ])
  })

  it('keeps current keyword ids unchanged', () => {
    expect(migrateKeywords(['9828', 'Combustion'])).toEqual(['9828', 'Combustion'])
  })

  it('preserves unknown/removed keyword ids for the strict tier to reject', () => {
    expect(migrateKeywords(['Combustion', 'GhostKeyword'])).toEqual(['Combustion', 'GhostKeyword'])
  })

  it('collapses a legacy id and its current id to a single member', () => {
    expect(migrateKeywords(['AccelBullet', '9828'])).toEqual(['9828'])
  })

  it('returns an empty array for non-array input', () => {
    expect(migrateKeywords(undefined)).toEqual([])
    expect(migrateKeywords(null)).toEqual([])
    expect(migrateKeywords('AccelBullet')).toEqual([])
  })

  it('ignores non-string array members', () => {
    expect(migrateKeywords(['Combustion', 42, null])).toEqual(['Combustion'])
  })
})
