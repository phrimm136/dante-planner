import { invalidFieldType, isInteger, parseList } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { byPathThenCode } from '../violation'

const MIN_DEPLOYMENT_SINNER = 0
const MAX_DEPLOYMENT_SINNER = 11

const DEPLOYMENT_ORDER_PATH = contentPath(field('deploymentOrder'))

export type DeploymentViolation =
  | InvalidFieldType
  | { code: Codes<'OUT_OF_RANGE_DEPLOYMENT'>; path: ContentPath }

const inRange = (sinnerIndex: number) =>
  sinnerIndex >= MIN_DEPLOYMENT_SINNER && sinnerIndex <= MAX_DEPLOYMENT_SINNER

function entryViolations(value: unknown, position: number): DeploymentViolation[] {
  const path = extendPath(DEPLOYMENT_ORDER_PATH, index(position))
  if (!isInteger(value)) return [invalidFieldType(path)]
  return inRange(value) ? [] : [{ code: 'OUT_OF_RANGE_DEPLOYMENT', path }]
}

export function deploymentOrderViolations(raw: unknown): DeploymentViolation[] {
  const { elements, violations } = parseList(raw, DEPLOYMENT_ORDER_PATH)
  return [...violations, ...elements.flatMap(entryViolations)].sort(byPathThenCode)
}
