import { describe, expect, it } from 'vitest'
import type { z } from 'zod'

import md7Card from '@static/data/MD7/startBuffCard.json'
import { StartBuffCardLayoutSchema, StartBuffMiniCardLayoutSchema } from '../StartBuffCardSchemas'

const CARDS = import.meta.glob<unknown>('@static/data/MD*/startBuffCard.json', {
  eager: true,
  import: 'default',
})
const MINI_CARDS = import.meta.glob<unknown>('@static/data/MD*/startBuffMiniCard.json', {
  eager: true,
  import: 'default',
})

const MD7_PREFAB = '[Script]StartBuffCardUIWithLevel_MD7'

function failures(files: Record<string, unknown>, schema: z.ZodType): string[] {
  return Object.entries(files).flatMap(([file, data]) => {
    const result = schema.safeParse(data)
    return result.success ? [] : [`${file}: ${result.error.message}`]
  })
}

function messages(data: unknown): string[] {
  const result = StartBuffCardLayoutSchema.safeParse(data)
  return result.success ? [] : result.error.issues.map((issue) => issue.message)
}

describe('real layout files', () => {
  it.each([
    ['startBuffCard', CARDS, StartBuffCardLayoutSchema],
    ['startBuffMiniCard', MINI_CARDS, StartBuffMiniCardLayoutSchema],
  ] as const)('parses every MD*/%s.json', (_, files, schema) => {
    expect(Object.keys(files).length).toBeGreaterThan(0)
    expect(failures(files, schema)).toEqual([])
  })
})

describe('StartBuffCardLayoutSchema', () => {
  it('refuses a layout without a desc text', () => {
    const raw = { ...md7Card, elements: md7Card.elements.filter((e) => e.role !== 'desc') }

    expect(messages(raw)).toContain(`${MD7_PREFAB} has no desc text`)
  })

  it('refuses a layout without a description viewport', () => {
    const raw = {
      ...md7Card,
      elements: md7Card.elements.filter((e) => e.role !== 'descViewport'),
    }

    expect(messages(raw)).toContain(`${MD7_PREFAB} has no description viewport`)
  })

  it('refuses a sliced sprite without pixelsPerUnit', () => {
    const sliced = md7Card.elements.findIndex((e) => 'image' in e && e.image.slice !== null)
    const raw = {
      ...md7Card,
      elements: md7Card.elements.map((e, index) =>
        index === sliced && 'image' in e ? { ...e, image: { ...e.image, pixelsPerUnit: null } } : e,
      ),
    }

    const result = StartBuffCardLayoutSchema.safeParse(raw)

    expect(sliced).toBeGreaterThanOrEqual(0)
    expect(result.success).toBe(false)
    expect(result.error?.issues.map((issue) => issue.path)).toEqual([['elements', sliced, 'image']])
  })

  it('carries the desc text and the description viewport', () => {
    const layout = StartBuffCardLayoutSchema.parse(md7Card)
    const viewport = layout.elements.find((e) => e.role === 'descViewport')

    expect(layout.desc.role).toBe('desc')
    expect(layout.viewport).toEqual(viewport?.rect)
  })
})
