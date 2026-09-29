import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

import plannerMessages from '@static/i18n/EN/planner.json'
import { VALIDATION_CODES } from '../codes'
import type { ValidationCode } from '../codes'
import { contentPath, field, index, key } from '../contentPath'
import { toUserFriendlyError, validationMessageKey } from '../plannerValidationErrors'
import type { PlannerValidationError } from '../plannerValidationErrors'

const FLOOR_0 = contentPath(field('floorSelections'), index(0))

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../../..',
)
const VOCABULARY_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-error-codes.json')

describe('toUserFriendlyError', () => {
  it('MISSING_TITLE → missingTitle key', () => {
    const result = toUserFriendlyError({ code: 'MISSING_TITLE', path: contentPath(field('title')) })
    expect(result).toEqual({ key: 'pages.plannerMD.publish.missingTitle' })
  })

  it.each(['INSUFFICIENT_FLOORS', 'MISSING_THEMEPACK'] as const)(
    '%s → missingThemePack key',
    (code) => {
      expect(toUserFriendlyError({ code, path: FLOOR_0 }).key).toBe(
        'pages.plannerMD.publish.missingThemePack',
      )
    },
  )

  it('UNAFFORDABLE_EGOGIFT → themePackEgoGiftInconsistency key with pack/gifts params', () => {
    const result = toUserFriendlyError({
      code: 'UNAFFORDABLE_EGOGIFT',
      path: contentPath(field('floorSelections'), index(2), field('giftIds'), index(0)),
      params: { pack: '1001', gifts: 'Gift A' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.publish.themePackEgoGiftInconsistency',
      params: { pack: '1001', gifts: 'Gift A' },
    })
  })

  it('UNKNOWN_EGOGIFT_FLOOR → unknownGiftId key with floor and gifts params', () => {
    const result = toUserFriendlyError({
      code: 'UNKNOWN_EGOGIFT_FLOOR',
      path: contentPath(field('floorSelections'), index(1), field('giftIds'), index(0)),
      params: { floor: '2', gifts: '2029' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.unknownGiftId',
      params: { floor: '2', gifts: '2029' },
    })
  })

  it.each([
    ['INSUFFICIENT_DIFFICULTY_ENTRY', 'pages.plannerMD.publish.requiresHardMode'],
    ['OUT_OF_RANGE_DIFFICULTY_NOT_HARD', 'pages.plannerMD.publish.hardModeOnly'],
    ['OUT_OF_RANGE_DIFFICULTY_NOT_EXTREME', 'pages.plannerMD.publish.extremeModeOnly'],
    ['OUT_OF_RANGE_DIFFICULTY', 'pages.plannerMD.validation.corruptedState'],
    ['OUT_OF_ORDER_DIFFICULTY', 'pages.plannerMD.publish.normalAfterHard'],
    ['OUT_OF_ORDER_THEMEPACK', 'pages.plannerMD.previousFloorNoThemePack'],
  ] as const)('%s → %s', (code, key) => {
    expect(toUserFriendlyError({ code, path: FLOOR_0 })).toEqual({ key })
  })

  it.each([
    ['UNKNOWN_THEMEPACK', 'pages.plannerMD.validation.unknownThemePackId'],
    ['UNKNOWN_IDENTITY', 'pages.plannerMD.validation.unknownIdentityId'],
    ['UNKNOWN_EGO', 'pages.plannerMD.validation.unknownEgoId'],
    ['UNKNOWN_STARTBUFF', 'pages.plannerMD.validation.unknownStartBuffId'],
  ] as const)('%s → %s with the id param', (code, key) => {
    expect(toUserFriendlyError({ code, path: FLOOR_0, params: { id: '9999' } })).toEqual({
      key,
      params: { id: '9999' },
    })
  })

  it('UNKNOWN_KEYWORD → invalidKeyword key with keyword param', () => {
    const result = toUserFriendlyError({
      code: 'UNKNOWN_KEYWORD',
      path: contentPath(field('selectedKeywords')),
      params: { keyword: 'GhostKeyword' },
    })
    expect(result).toEqual({
      key: 'pages.plannerMD.validation.invalidKeyword',
      params: { keyword: 'GhostKeyword' },
    })
  })

  it.each([
    { code: 'MISSING_REQUIRED_FIELD', path: contentPath(field('equipment'), key('1')) },
    { code: 'UNKNOWN_FIELD', path: contentPath(field('equipment'), key('01')) },
    { code: 'OUT_OF_RANGE_DEPLOYMENT', path: contentPath(field('deploymentOrder'), index(0)) },
    { code: 'DUPLICATE_EGOGIFT', path: contentPath(field('observationGiftIds'), index(1)) },
    { code: 'EXCESS_STARTBUFF', path: contentPath(field('selectedBuffIds')) },
    { code: 'DUPLICATE_STARTGIFT', path: contentPath(field('selectedGiftIds'), index(1)) },
    { code: 'INVALID_FIELD_TYPE', path: contentPath(field('equipment')) },
    { code: 'DUPLICATE_THEMEPACK', path: FLOOR_0 },
  ] satisfies PlannerValidationError[])('$code → corruptedState key', (error) => {
    expect(toUserFriendlyError(error)).toEqual({
      key: 'pages.plannerMD.validation.corruptedState',
    })
  })
})

describe('validationMessageKey', () => {
  const vocabulary = JSON.parse(fs.readFileSync(VOCABULARY_PATH, 'utf-8')) as {
    codes: Record<string, { emittedBy: string[] }>
  }
  const frontendCodes = new Set(
    Object.entries(vocabulary.codes)
      .filter(([, entry]) => entry.emittedBy.includes('frontend'))
      .map(([code]) => code),
  )
  const isFrontendCode = (code: ValidationCode): code is PlannerValidationError['code'] =>
    frontendCodes.has(code)

  const isRecord = (node: unknown): node is Record<string, unknown> =>
    typeof node === 'object' && node !== null

  const plannerMessage = (key: string): unknown =>
    key
      .split('.')
      .reduce<unknown>((node, part) => (isRecord(node) ? node[part] : undefined), plannerMessages)

  it.each(VALIDATION_CODES.filter(isFrontendCode))('%s resolves to a planner message', (code) => {
    expect(typeof plannerMessage(validationMessageKey(code))).toBe('string')
  })
})
