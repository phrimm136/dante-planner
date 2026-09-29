import { z } from 'zod'
import { err, ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import { MDPlannerContentSchema } from '../../schemas/PlannerSchemas'
import { contentPath, extendPath, field, index, key } from './contentPath'
import type { ContentPath, ContentPathSegment } from './contentPath'
import { byPathThenCode } from './violation'
import type { ShapeCode } from './violation'

export type PlannerContentValue = z.infer<typeof MDPlannerContentSchema>

export type SinnerEquipmentValue =
  PlannerContentValue['equipment'][keyof PlannerContentValue['equipment']]

export type SkillPlanValue =
  PlannerContentValue['skillEAState'][keyof PlannerContentValue['skillEAState']]

export type SectionNotesValue = PlannerContentValue['sectionNotes']

export type ShapeViolation = { code: ShapeCode; path: ContentPath }

type RawPath = readonly PropertyKey[]

type Walk = { schema: z.ZodType | undefined; path: ContentPath }

const FLOOR_SELECTIONS = 'floorSelections'

const NUMERAL = /^\d+$/

const unwrapped = (schema: z.ZodType): z.ZodType =>
  schema instanceof z.ZodOptional || schema instanceof z.ZodNullable
    ? unwrapped(schema.unwrap() as z.ZodType)
    : schema

function childOf(schema: z.ZodType, segment: PropertyKey): z.ZodType | undefined {
  if (schema instanceof z.ZodArray) return schema.element as z.ZodType
  if (schema instanceof z.ZodRecord) return schema.valueType as z.ZodType
  if (schema instanceof z.ZodObject) return schema.shape[String(segment)] as z.ZodType | undefined
  return undefined
}

const holdsKeys = (schema: z.ZodType): boolean =>
  schema instanceof z.ZodRecord ||
  (schema instanceof z.ZodObject && Object.keys(schema.shape).every((name) => NUMERAL.test(name)))

function segmentOf(schema: z.ZodType | undefined, segment: PropertyKey): ContentPathSegment {
  if (typeof segment === 'number') return index(segment)
  const name = String(segment)
  return schema !== undefined && holdsKeys(schema) ? key(name) : field(name)
}

function step(walk: Walk, segment: PropertyKey): Walk {
  const schema = walk.schema === undefined ? undefined : unwrapped(walk.schema)
  return {
    schema: schema === undefined ? undefined : childOf(schema, segment),
    path: extendPath(walk.path, segmentOf(schema, segment)),
  }
}

const pathOf = (schema: z.ZodType, rawPath: RawPath, at: ContentPath): ContentPath =>
  rawPath.reduce<Walk>(step, { schema, path: at }).path

const childValue = (node: unknown, segment: PropertyKey): unknown =>
  typeof node === 'object' && node !== null && Object.hasOwn(node, segment)
    ? (node as Record<PropertyKey, unknown>)[segment]
    : undefined

const valueAt = (input: unknown, rawPath: RawPath): unknown =>
  rawPath.reduce<unknown>(childValue, input)

type Parse = { schema: z.ZodType; input: unknown; at: ContentPath }

function issueViolations(issue: z.core.$ZodIssue, parse: Parse): ShapeViolation[] {
  const { schema, input, at } = parse
  if (issue.code === 'unrecognized_keys') {
    return issue.keys.map((name) => ({
      code: 'UNKNOWN_FIELD',
      path: pathOf(schema, [...issue.path, name], at),
    }))
  }
  const missing = issue.code === 'invalid_type' && valueAt(input, issue.path) === undefined
  return [
    {
      code: missing ? 'MISSING_REQUIRED_FIELD' : 'INVALID_FIELD_TYPE',
      path: pathOf(schema, issue.path, at),
    },
  ]
}

function parseShape<S extends z.ZodType>(
  schema: S,
  input: unknown,
  at: ContentPath,
): Result<z.infer<S>, ShapeViolation[]> {
  const parsed = schema.safeParse(input)
  if (parsed.success) return ok(parsed.data)
  const parse: Parse = { schema, input, at }
  const violations = parsed.error.issues.flatMap((issue) => issueViolations(issue, parse))
  return err(violations.sort(byPathThenCode))
}

export const parseContent = (content: unknown): Result<PlannerContentValue, ShapeViolation[]> =>
  parseShape(MDPlannerContentSchema, content, contentPath())

export const parseFloorSelections = (raw: unknown) =>
  parseShape(
    MDPlannerContentSchema.shape.floorSelections,
    raw,
    contentPath(field(FLOOR_SELECTIONS)),
  )
