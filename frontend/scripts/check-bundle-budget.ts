import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { bundleBudgetSchema, checkEntryBudget } from './lib/entryBudget'

const FRONTEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const DIST_ROOT = path.join(FRONTEND_ROOT, 'dist')
const BUDGET_FILE = 'bundle-budget.json'

const { entryGzipBytes } = bundleBudgetSchema.parse(
  JSON.parse(readFileSync(path.join(FRONTEND_ROOT, BUDGET_FILE), 'utf8')),
)
const result = checkEntryBudget(
  readFileSync(path.join(DIST_ROOT, 'index.html'), 'utf8'),
  (src) => readFileSync(path.join(DIST_ROOT, src)),
  entryGzipBytes,
)

if (result.ok) {
  console.log(
    `Entry chunk ${result.file} is ${result.gzipBytes} B gzip, within the ${result.budget} B budget in ${BUDGET_FILE}`,
  )
} else {
  console.error(
    `Entry chunk ${result.file} is ${result.gzipBytes} B gzip, over the ${result.budget} B budget in ${BUDGET_FILE}`,
  )
  process.exit(1)
}
