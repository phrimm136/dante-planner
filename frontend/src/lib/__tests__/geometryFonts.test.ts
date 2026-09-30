/// <reference types="node" />
/* eslint-disable @typescript-eslint/no-require-imports */
import { describe, it, expect } from 'vitest'

import { getDisplayFontForLanguage, getGeometryFontForLanguage } from '../utils'

const fs = require('fs') as typeof import('fs')
const path = require('path') as typeof import('path')

const STYLES_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../styles')
const FONT_FACE_CSS = ['globals.css', 'korean-font-coverage.css']
  .map((file) => fs.readFileSync(path.join(STYLES_DIR, file), 'utf-8'))
  .join('\n')

const DISPLAY_LANGUAGES = ['KR', 'EN', 'JP', 'CN'] as const

function declaredFamilies(css: string): string[] {
  return [...css.matchAll(/font-family:\s*['"]([^'"]+)['"]/g)].map((found) => found[1] ?? '')
}

function geometryFamily(language: string): string {
  const family = getGeometryFontForLanguage(language).fontFamily ?? ''
  return family.match(/^'([^']+)'/)?.[1] ?? ''
}

describe('getGeometryFontForLanguage', () => {
  it.each(DISPLAY_LANGUAGES)('names a %s family an @font-face rule declares', (language) => {
    const declared = declaredFamilies(FONT_FACE_CSS)

    expect(geometryFamily(language)).toMatch(/ Geo$/)
    expect(declared).toContain(geometryFamily(language))
  })

  it.each(DISPLAY_LANGUAGES)('falls back to the %s display stack', (language) => {
    expect(getGeometryFontForLanguage(language).fontFamily).toContain(
      String(getDisplayFontForLanguage(language).fontFamily),
    )
  })

  it('uses the display face for a language with no geometry face', () => {
    expect(getGeometryFontForLanguage('RU')).toEqual(getDisplayFontForLanguage('RU'))
  })
})
