/// <reference types="node" />
/**
 * Entity-section conformance.
 *
 * Every database section's entity is its spec entry plus a branded id, built from
 * the real static data so a spec key no builder carries fails here.
 */

/* eslint-disable @typescript-eslint/no-require-imports */
import { describe, it, expect } from 'vitest'

import { BATTLE_FAMILIES } from '@/pages/battle'
import type { AnyEntitySection } from '@/shared/filter'

const fs = require('fs') as typeof import('fs')
const path = require('path') as typeof import('path')

const STATIC_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../static')
const DATA_DIR = path.join(STATIC_DIR, 'data')

const SECTIONS = Object.values(
  import.meta.glob<{ section: AnyEntitySection }>('/src/pages/*/lib{,/*}/entitySection.ts', {
    eager: true,
  }),
).map((m) => m.section)

/**
 * A database section ships both a facet table and a list component; the planner
 * filters a deck with facets but has no catalogue behind it.
 */
const dirsOf = (pattern: Record<string, unknown>): string[] =>
  Object.keys(pattern).map((p) => p.split('/')[3] ?? '')

const FACET_DIRS = dirsOf(import.meta.glob('/src/pages/*/lib/*Filter.ts')).filter((dir) =>
  dirsOf(import.meta.glob('/src/pages/*/components/*List.tsx')).includes(dir),
)

const SHARED_FACET_SLICE = 'battle'

const EXPECTED_SECTIONS = FACET_DIRS.flatMap((dir) =>
  dir === SHARED_FACET_SLICE ? BATTLE_FAMILIES.map((family) => `${dir}/${family}`) : [dir],
)

function readSpecList(section: AnyEntitySection): [string, Record<string, unknown>][] {
  const raw: unknown = JSON.parse(
    fs.readFileSync(path.join(DATA_DIR, section.specFile), 'utf-8') as string,
  )
  return Object.entries(section.specListSchema.parse(raw)) as [string, Record<string, unknown>][]
}

describe('entity sections', () => {
  it('every section with a facet table declares an entitySection', () => {
    const declared = SECTIONS.map((s) => s.name)
    expect([...declared].sort()).toEqual([...EXPECTED_SECTIONS].sort())
  })
})

describe.each(SECTIONS)('$name', (section) => {
  const entries = readSpecList(section)
  const keys = Object.keys(section.specEntrySchema.shape)
  const entityOf = (id: string, spec: Record<string, unknown>): Record<string, unknown> =>
    section.toEntity(id, spec) as unknown as Record<string, unknown>

  it.each(keys)('carries %s onto the entity', (key) => {
    const found = entries.find(([, spec]) => spec[key] !== undefined)
    expect(found, `no entry in ${section.specFile} sets ${key}`).toBeDefined()
    const [id, spec] = found as [string, Record<string, unknown>]
    expect(entityOf(id, spec)[key]).toEqual(spec[key])
  })

  it('leaves no key undefined', () => {
    const present: string[] = []
    for (const [id, spec] of entries) {
      const entity = entityOf(id, spec)
      present.push(
        ...keys
          .filter((key) => key in entity && entity[key] === undefined)
          .map((key) => `${id}.${key}`),
      )
    }
    expect(present).toEqual([])
  })

  it('every facet reads a defined value from every entity', () => {
    for (const [id, spec] of entries) {
      const entity = section.toEntity(id, spec)
      for (const [index, facet] of section.facets.entries()) {
        expect(facet.get(entity), `${id} facet ${index}`).toBeDefined()
      }
    }
  })

  it('id is the branded parse of the record key', () => {
    for (const [id, spec] of entries) {
      expect(entityOf(id, spec).id).toEqual(section.idSchema.parse(id))
    }
  })
})
