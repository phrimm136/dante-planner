import { invalidFieldType, isInteger, parseList } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

const MIN_DEPLOYMENT_SINNER = 0
const MAX_DEPLOYMENT_SINNER = 11
const MAX_DEPLOYMENT_ENTRIES = MAX_DEPLOYMENT_SINNER - MIN_DEPLOYMENT_SINNER + 1

const DEPLOYMENT_ORDER_PATH = contentPath(field('deploymentOrder'))

export type DeploymentViolation =
  | InvalidFieldType
  | {
      code: Codes<'OUT_OF_RANGE_DEPLOYMENT' | 'DUPLICATE_DEPLOYMENT' | 'EXCESS_DEPLOYMENT'>
      path: ContentPath
    }

const inRange = (sinnerIndex: number) =>
  sinnerIndex >= MIN_DEPLOYMENT_SINNER && sinnerIndex <= MAX_DEPLOYMENT_SINNER

const countedSinner = (value: unknown): number | undefined =>
  isInteger(value) && inRange(value) ? value : undefined

function entryViolations(
  value: unknown,
  position: number,
  firstPositions: ReadonlyMap<number | undefined, number>,
): DeploymentViolation[] {
  const path = extendPath(DEPLOYMENT_ORDER_PATH, index(position))
  if (!isInteger(value)) return [invalidFieldType(path)]
  if (!inRange(value)) return [{ code: 'OUT_OF_RANGE_DEPLOYMENT', path }]
  return firstPositions.get(value) === position ? [] : [{ code: 'DUPLICATE_DEPLOYMENT', path }]
}

const excessViolations = (entryCount: number): DeploymentViolation[] =>
  entryCount > MAX_DEPLOYMENT_ENTRIES
    ? [{ code: 'EXCESS_DEPLOYMENT', path: DEPLOYMENT_ORDER_PATH }]
    : []

export function deploymentOrderViolations(raw: unknown): DeploymentViolation[] {
  const { elements, violations } = parseList(raw, DEPLOYMENT_ORDER_PATH)
  const firstPositions = firstIndexOf(elements.map(countedSinner))
  return [
    ...violations,
    ...excessViolations(elements.length),
    ...elements.flatMap((value, position) => entryViolations(value, position, firstPositions)),
  ].sort(byPathThenCode)
}
