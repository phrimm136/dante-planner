import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

const LIB_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..')
const ENTRY_GRAPH_SPECIFIERS = ['@/lib/api', '@/lib/apiErrors']

function importSpecifiers(source: string): string[] {
  const statics = [...source.matchAll(/\bfrom\s+['"]([^'"]+)['"]/g)].map((match) => match[1]!)
  const sideEffects = [...source.matchAll(/^\s*import\s+['"]([^'"]+)['"]/gm)].map(
    (match) => match[1]!,
  )
  const dynamics = [...source.matchAll(/\bimport\s*\(\s*['"`]([^'"`]+)['"`]/g)].map(
    (match) => match[1]!,
  )
  return [...statics, ...sideEffects, ...dynamics]
}

describe('modules the published planner route loader imports statically', () => {
  it.each(['fetchPublishedPlannerRaw.ts', 'publishedPlannerQueryKeys.ts'])(
    '%s imports nothing outside the entry graph',
    (file) => {
      const source = fs.readFileSync(path.join(LIB_DIR, file), 'utf-8')

      const outside = importSpecifiers(source).filter(
        (specifier) => !ENTRY_GRAPH_SPECIFIERS.includes(specifier),
      )

      expect(outside).toEqual([])
    },
  )

  it('recognises each import form it guards against', () => {
    const source = [
      "import { a } from '../schemas/PlannerSchemas'",
      "import type { B } from '@/lib/validation'",
      "import 'zod'",
      "const c = await import('@/pages/planner/hooks/usePublishedPlannerQuery')",
      "export { d } from './d'",
    ].join('\n')

    expect(importSpecifiers(source)).toEqual([
      '../schemas/PlannerSchemas',
      '@/lib/validation',
      './d',
      'zod',
      '@/pages/planner/hooks/usePublishedPlannerQuery',
    ])
  })
})
