import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { z } from 'zod'

import { MDPlannerContentSchema } from '../PlannerSchemas'

const FRONTEND_ROOT = path.resolve(
  path.dirname(new URL(import.meta.url).pathname),
  '../../../../..',
)
const SCHEMA_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-content.schema.json')

describe('planner content JSON Schema export', () => {
  it('Given the Zod schema, When exported, Then it equals testdata/planner-content.schema.json', () => {
    const committed: unknown = JSON.parse(fs.readFileSync(SCHEMA_PATH, 'utf-8'))

    expect(committed).toEqual(z.toJSONSchema(MDPlannerContentSchema, { target: 'draft-2020-12' }))
  })
})
