import { writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { z } from 'zod'
import { MDPlannerContentSchema } from '../src/pages/planner/schemas/PlannerSchemas'

const FRONTEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const OUTPUT_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-content.schema.json')

const exported = z.toJSONSchema(MDPlannerContentSchema, { target: 'draft-2020-12' })

writeFileSync(OUTPUT_PATH, `${JSON.stringify(exported, null, 2)}\n`)
console.log(`Wrote ${OUTPUT_PATH}`)
