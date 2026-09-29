import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

import {
  compareContentPaths,
  contentPath,
  extendPath,
  field,
  index,
  key,
  renderContentPath,
} from '../contentPath'
import type { ContentPath, ContentPathSegment } from '../contentPath'

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../../..',
)
const RECORDED_PATH_FILES = [
  'planner-floor-rules.corpus.json',
  'floor-exchange-frontend.json',
  'floor-exchange-backend.json',
].map((file) => path.resolve(FRONTEND_ROOT, '../testdata', file))

const HEAD_FIELD = /^[A-Za-z0-9_]+/
const NEXT_SEGMENT = /^(?:\.([A-Za-z0-9_]+)|\[([^\]]+)\])/
const CANONICAL_INDEX = /^(0|[1-9][0-9]*)$/

function recordedPaths(value: unknown): string[] {
  if (Array.isArray(value)) return value.flatMap(recordedPaths)
  if (typeof value !== 'object' || value === null) return []
  return Object.entries(value).flatMap(([name, nested]) =>
    name === 'path' && typeof nested === 'string' ? [nested] : recordedPaths(nested),
  )
}

function built(text: string): ContentPath {
  const head = text.match(HEAD_FIELD)?.[0]
  if (head === undefined) throw new Error(`'${text}' has no leading field`)
  const segments: ContentPathSegment[] = [field(head)]
  let rest = text.slice(head.length)
  while (rest.length > 0) {
    const match = rest.match(NEXT_SEGMENT)
    if (match === null) throw new Error(`'${text}' does not tokenize at '${rest}'`)
    const [whole, dotted, bracketed] = match
    if (dotted !== undefined) segments.push(field(dotted))
    else if (bracketed !== undefined && CANONICAL_INDEX.test(bracketed))
      segments.push(index(Number(bracketed)))
    else if (bracketed !== undefined) segments.push(key(bracketed))
    rest = rest.slice(whole.length)
  }
  return contentPath(...segments)
}

const floorsPath = contentPath(field('floorSelections'))
const giftsPath = extendPath(floorsPath, index(0), field('giftIds'))

describe('renderContentPath', () => {
  it('renders an empty path as empty text', () => {
    expect(renderContentPath(contentPath())).toBe('')
  })

  it('writes fields dotted and indices and keys bracketed', () => {
    expect(renderContentPath(floorsPath)).toBe('floorSelections')
    expect(renderContentPath(extendPath(floorsPath, index(3), field('giftIds'), index(10)))).toBe(
      'floorSelections[3].giftIds[10]',
    )
    expect(renderContentPath(contentPath(field('equipment'), key('01'), field('identity')))).toBe(
      'equipment[01].identity',
    )
  })

  it('reproduces every path the corpus and exchange files record when built segment by segment', () => {
    const recorded = new Set(
      RECORDED_PATH_FILES.flatMap((file) =>
        recordedPaths(JSON.parse(fs.readFileSync(file, 'utf-8')) as unknown),
      ),
    )
    expect([...recorded]).toEqual(
      expect.arrayContaining(['floorSelections', 'floorSelections[14]', 'category']),
    )
    const mismatches = [...recorded].filter((text) => renderContentPath(built(text)) !== text)
    expect(mismatches).toEqual([])
  })
})

describe('compareContentPaths', () => {
  it('orders indices numerically, so 2 precedes 10', () => {
    const two = extendPath(giftsPath, index(2))
    const ten = extendPath(giftsPath, index(10))
    expect(compareContentPaths(two, ten)).toBe(-1)
    expect(compareContentPaths(ten, two)).toBe(1)
  })

  it('orders a path before its extensions and equal paths as equal', () => {
    const floor = extendPath(floorsPath, index(1))
    expect(compareContentPaths(floorsPath, floor)).toBe(-1)
    expect(compareContentPaths(extendPath(floor, field('difficulty')), floor)).toBe(1)
    expect(compareContentPaths(floor, extendPath(floorsPath, index(1)))).toBe(0)
  })

  it('orders fields by name and kinds as field, index, key', () => {
    const floor = extendPath(floorsPath, index(0))
    const equipment = contentPath(field('equipment'))
    expect(
      compareContentPaths(
        extendPath(floor, field('difficulty')),
        extendPath(floor, field('giftIds')),
      ),
    ).toBe(-1)
    expect(
      compareContentPaths(extendPath(equipment, field('z')), extendPath(equipment, index(0))),
    ).toBe(-1)
    expect(
      compareContentPaths(extendPath(equipment, index(99)), extendPath(equipment, key('01'))),
    ).toBe(-1)
    expect(
      compareContentPaths(extendPath(equipment, key('02')), extendPath(equipment, key('01'))),
    ).toBe(1)
  })
})
