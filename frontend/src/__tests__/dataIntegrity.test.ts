/// <reference types="node" />
/**
 * Data Integrity Tests
 *
 * Validates every JSON file in static/data/ and static/i18n/ against
 * the corresponding Zod schemas. Catches schema-data mismatches that
 * would cause runtime validation failures on the frontend.
 */

/* eslint-disable @typescript-eslint/no-require-imports */
import { describe, it, expect } from 'vitest'
import type { ZodSchema } from 'zod'

const fs = require('fs') as typeof import('fs')
const path = require('path') as typeof import('path')

import {
  IdentityDataSchema,
  IdentityI18nSchema,
  IdentitySpecListSchema,
  IdentityNameListSchema,
} from '@/pages/identity'
import {
  EGODataSchema,
  EGOI18nSchema,
  EGOSpecListSchema,
  EGONameListSchema,
} from '@/pages/ego'
import {
  EGOGiftDataSchema,
  EGOGiftI18nSchema,
  EGOGiftSpecListSchema,
  EGOGiftNameListSchema,
} from '@/pages/egoGift'
import {
  ThemePackDetailSchema,
  ThemePackListSchema,
  ThemePackI18nSchema,
} from '@/pages/themePack'
import {
  BattleKeywordsSchema,
  BattleKeywordSpecListSchema,
  ColorCodeMapSchema,
} from '@/shared/gameText'
import {
  AnnouncementSpecListSchema,
  AnnouncementI18nSchema,
} from '@/pages/home'
import {
  StartBuffDataListSchema,
  StartBuffI18nSchema,
} from '@/shared/gameText'
import {
  AbEventSpecListSchema,
  AbEventDataSchema,
  AbEventI18nSchema,
  AbEventSharedSchema,
} from '@/pages/abEvent'

const STATIC_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../static')
const DATA_DIR = path.join(STATIC_DIR, 'data')
const I18N_DIR = path.join(STATIC_DIR, 'i18n')
const LANGS = ['EN', 'KR', 'JP', 'CN']

function readJson(filePath: string): unknown {
  return JSON.parse(fs.readFileSync(filePath, 'utf-8'))
}

function listJsonFiles(dir: string): string[] {
  if (!fs.existsSync(dir)) return []
  return fs.readdirSync(dir)
    .filter((f: string) => f.endsWith('.json'))
    .map((f: string) => path.join(dir, f))
}

/** Validate a single file against a schema */
function validateFile(filePath: string, schema: ZodSchema) {
  const data = readJson(filePath)
  const result = schema.safeParse(data)
  if (!result.success) {
    console.error(path.basename(filePath), result.error.issues.slice(0, 3))
  }
  expect(result.success).toBe(true)
}

/** Create tests for every JSON file in a directory */
function testDirectory(dir: string, schema: ZodSchema) {
  const files = listJsonFiles(dir)
  if (files.length === 0) {
    it('no files found (skipped)', () => {})
    return
  }
  for (const filePath of files) {
    it(path.basename(filePath), () => validateFile(filePath, schema))
  }
}

/** Validate a single file if it exists */
function testFileIfExists(filePath: string, schema: ZodSchema) {
  it(path.basename(filePath), () => {
    if (!fs.existsSync(filePath)) return
    validateFile(filePath, schema)
  })
}

// --- Spec List files (single aggregated files) ---

describe('spec list files', () => {
  it('identitySpecList.json', () => validateFile(path.join(DATA_DIR, 'identitySpecList.json'), IdentitySpecListSchema))
  it('egoSpecList.json', () => validateFile(path.join(DATA_DIR, 'egoSpecList.json'), EGOSpecListSchema))
  it('egoGiftSpecList.json', () => validateFile(path.join(DATA_DIR, 'egoGiftSpecList.json'), EGOGiftSpecListSchema))
  it('themePackList.json', () => validateFile(path.join(DATA_DIR, 'themePackList.json'), ThemePackListSchema))
  it('abEventSpecList.json', () => validateFile(path.join(DATA_DIR, 'abEventSpecList.json'), AbEventSpecListSchema))
  it('battleKeywordSpecList.json', () => validateFile(path.join(DATA_DIR, 'battleKeywordSpecList.json'), BattleKeywordSpecListSchema))
  it('colorCode.json', () => validateFile(path.join(DATA_DIR, 'colorCode.json'), ColorCodeMapSchema))
  it('announcements.json', () => validateFile(path.join(DATA_DIR, 'announcements.json'), AnnouncementSpecListSchema))
})

// --- Mirror Dungeon versioned data files ---

const MD_DIRS = fs.readdirSync(DATA_DIR).filter((d: string) => /^MD\d+$/.test(d))

describe.each(MD_DIRS)('start buff data [%s]', (mdDir) => {
  it('startBuffs.json', () => validateFile(path.join(DATA_DIR, mdDir, 'startBuffs.json'), StartBuffDataListSchema))
})

// --- Individual data files ---

describe('identity data files', () => testDirectory(path.join(DATA_DIR, 'identity'), IdentityDataSchema))
describe('ego data files', () => testDirectory(path.join(DATA_DIR, 'ego'), EGODataSchema))
describe('egoGift data files', () => testDirectory(path.join(DATA_DIR, 'egoGift'), EGOGiftDataSchema))
describe('themePack data files', () => testDirectory(path.join(DATA_DIR, 'themePack'), ThemePackDetailSchema))
describe('abEvent data files', () => testDirectory(path.join(DATA_DIR, 'abEvent'), AbEventDataSchema))

// --- i18n files per language ---

describe.each(LANGS)('i18n [%s]', (lang) => {
  const langDir = path.join(I18N_DIR, lang)

  testFileIfExists(path.join(langDir, 'identityNameList.json'), IdentityNameListSchema)
  testFileIfExists(path.join(langDir, 'egoNameList.json'), EGONameListSchema)
  testFileIfExists(path.join(langDir, 'egoGiftNameList.json'), EGOGiftNameListSchema)
  testFileIfExists(path.join(langDir, 'battleKeywords.json'), BattleKeywordsSchema)
  testFileIfExists(path.join(langDir, 'themePack.json'), ThemePackI18nSchema)
  testFileIfExists(path.join(langDir, 'abEvent', '_shared.json'), AbEventSharedSchema)
  testFileIfExists(path.join(langDir, 'announcements.json'), AnnouncementI18nSchema)
  for (const mdDir of MD_DIRS) {
    testFileIfExists(path.join(langDir, mdDir, 'startBuffs.json'), StartBuffI18nSchema)
  }

  describe('identity i18n', () => testDirectory(path.join(langDir, 'identity'), IdentityI18nSchema))
  describe('ego i18n', () => testDirectory(path.join(langDir, 'ego'), EGOI18nSchema))
  describe('egoGift i18n', () => testDirectory(path.join(langDir, 'egoGift'), EGOGiftI18nSchema))
  describe('abEvent i18n', () => testDirectory(path.join(langDir, 'abEvent'), AbEventI18nSchema))
})
