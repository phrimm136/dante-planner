import { EncodedGiftIdSchema, FLOOR_RULE_TABLE } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { giftDisplayName, hasGiftId } from '@/pages/egoGift'
import { err, ok } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { getUnaffordableGiftIds } from '../plannerRules'
import { isMDPlanner } from '../../types/PlannerTypes'
import type { MDSaveablePlanner, SaveablePlanner } from '../../types/PlannerTypes'
import { contentPath, field } from './contentPath'
import type { ContentPath } from './contentPath'
import { parseContent } from './contentShape'
import type { PlannerContentValue } from './contentShape'
import { keywordViolations, noteSizeViolations, titleViolations, unknownVersion } from './document'
import { egoRuleViolations } from './ego/egos'
import type { EgoReferenceData } from './ego/egos'
import {
  COMPREHENSIVE_GIFT_IDS_PATH,
  OBSERVATION_GIFT_IDS_PATH,
  giftIdViolations,
} from './egogift/giftIds'
import type { GiftReferenceData } from './egogift/giftIds'
import { floorReferenceViolations } from './floor/floorReferences'
import type { FloorReferenceData, FloorReferenceViolation } from './floor/floorReferences'
import { admitFloors, parseFloors } from './floor/floorRules'
import { identityRuleViolations } from './identity/identity'
import type { IdentityReferenceData } from './identity/identity'
import type { FloorReferenceError, PlannerValidationError } from './plannerValidationErrors'
import { deploymentOrderViolations } from './sinner/deploymentOrder'
import { sinnerEntries } from './sinnerEntries'
import { sinnerSkillViolations } from './skill/skillEAState'
import { startBuffViolations } from './startbuff/startBuffs'
import type { StartBuffSeason } from './startbuff/startBuffs'
import { START_GIFT_IDS_PATH, startGiftViolations } from './startgift/startGift'
import type { StartGiftSeason } from './startgift/startGift'
import { byPathThenCode } from './violation'

export interface PlannerIdRegistry {
  identityIds: ReadonlySet<string>
  egoIds: ReadonlySet<string>
  egoMaxThreadspin: ReadonlyMap<string, number>
  themePackIds: ReadonlySet<string>
  season:
    | {
        startBuffIds: ReadonlySet<string>
        startGiftPools: ReadonlyMap<string, ReadonlySet<string>>
      }
    | undefined
}

declare const validatedBrand: unique symbol

export type ValidatedPlanner = SaveablePlanner & { readonly [validatedBrand]: true }

type ContentStage = Exclude<FloorRuleStage, 'index'>

type ReferenceData = IdentityReferenceData & EgoReferenceData & GiftReferenceData

type SeasonReferenceData = StartBuffSeason & StartGiftSeason

type PlannerPlan = {
  stage: ContentStage
  content: PlannerContentValue
  category: MDCategory
  references: ReferenceData
  season: SeasonReferenceData
  floorReferences: FloorReferenceData
  egoGiftI18n: Record<string, string>
}

type PlannerCheck = (plan: PlannerPlan) => PlannerValidationError[]

const EQUIPMENT_PATH = contentPath(field('equipment'))
const SKILL_EA_STATE_PATH = contentPath(field('skillEAState'))
const FLOOR_INDEX_POSITION = 1

function knownGift(giftId: string, egoGiftSpec: Record<string, EGOGiftSpec>): boolean {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return encoded.success && hasGiftId(encoded.data, egoGiftSpec)
}

function affordableGift(
  giftId: string,
  themePackId: string,
  egoGiftSpec: Record<string, EGOGiftSpec>,
): boolean {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return (
    !encoded.success ||
    getUnaffordableGiftIds(new Set([encoded.data]), themePackId, egoGiftSpec).length === 0
  )
}

function referenceData(
  egoGiftSpec: Record<string, EGOGiftSpec>,
  registry: PlannerIdRegistry,
): ReferenceData {
  return {
    hasIdentity: (identityId) => registry.identityIds.has(identityId),
    hasEgo: (egoId) => registry.egoIds.has(egoId),
    maxThreadspin: (egoId) => registry.egoMaxThreadspin.get(egoId),
    hasEgoGift: (giftId) => knownGift(giftId, egoGiftSpec),
  }
}

function seasonReferenceData(registry: PlannerIdRegistry): SeasonReferenceData | undefined {
  const { season } = registry
  if (season === undefined) return undefined
  return {
    hasStartBuff: (buffId) => season.startBuffIds.has(buffId),
    hasStartGiftKeyword: (keyword) => season.startGiftPools.has(keyword),
    inStartGiftPool: (keyword, baseGiftId) =>
      season.startGiftPools.get(keyword)?.has(baseGiftId) === true,
  }
}

function floorReferenceData(
  egoGiftSpec: Record<string, EGOGiftSpec>,
  registry: PlannerIdRegistry,
): FloorReferenceData {
  return {
    hasThemePack: (themePackId) => registry.themePackIds.has(themePackId),
    hasEgoGift: (giftId) => knownGift(giftId, egoGiftSpec),
    isGiftAffordableForThemePack: (giftId, themePackId) =>
      affordableGift(giftId, themePackId, egoGiftSpec),
  }
}

function giftName(giftId: string, egoGiftI18n: Record<string, string>): string {
  const encoded = EncodedGiftIdSchema.safeParse(giftId)
  return encoded.success ? giftDisplayName(encoded.data, egoGiftI18n) : giftId
}

function floorNumberOf(path: ContentPath): string {
  const floor = path[FLOOR_INDEX_POSITION]
  return floor?.kind === 'index' ? String(floor.value + 1) : ''
}

function floorReferenceError(
  violation: FloorReferenceViolation,
  egoGiftI18n: Record<string, string>,
): FloorReferenceError {
  const { path } = violation
  switch (violation.code) {
    case 'UNKNOWN_THEMEPACK':
      return { code: violation.code, path, params: { id: violation.themePackId } }
    case 'UNKNOWN_EGOGIFT_FLOOR':
      return {
        code: violation.code,
        path,
        params: { floor: floorNumberOf(path), gifts: violation.giftId },
      }
    case 'UNAFFORDABLE_EGOGIFT':
      return {
        code: violation.code,
        path,
        params: { pack: violation.themePackId, gifts: giftName(violation.giftId, egoGiftI18n) },
      }
  }
}

const equippedEntities: PlannerCheck = ({ content, references }) => {
  const entries = sinnerEntries(content.equipment, EQUIPMENT_PATH)
  return [
    ...entries.flatMap((entry) => identityRuleViolations(entry, references)),
    ...entries.flatMap((entry) => egoRuleViolations(entry, references)),
  ]
}

const deploymentOrder: PlannerCheck = ({ content }) =>
  deploymentOrderViolations(content.deploymentOrder)

const skillEAState: PlannerCheck = ({ content }) =>
  sinnerEntries(content.skillEAState, SKILL_EA_STATE_PATH).flatMap((entry) =>
    sinnerSkillViolations(entry.value, entry.path),
  )

const giftIds: PlannerCheck = ({ content, references }) => [
  ...giftIdViolations(
    content.selectedGiftIds,
    START_GIFT_IDS_PATH,
    'DUPLICATE_STARTGIFT',
    references,
  ),
  ...giftIdViolations(
    content.observationGiftIds,
    OBSERVATION_GIFT_IDS_PATH,
    'DUPLICATE_EGOGIFT',
    references,
  ),
  ...giftIdViolations(
    content.comprehensiveGiftIds,
    COMPREHENSIVE_GIFT_IDS_PATH,
    'DUPLICATE_EGOGIFT',
    references,
  ),
]

const startBuffs: PlannerCheck = ({ content, season }) =>
  startBuffViolations(content.selectedBuffIds, season)

const startGift: PlannerCheck = ({ content, references, season }) =>
  startGiftViolations(content.selectedGiftKeyword, content.selectedGiftIds, references, season)

const keywords: PlannerCheck = ({ content }) => keywordViolations(content.selectedKeywords)

const floors: PlannerCheck = ({ content, category, stage, floorReferences, egoGiftI18n }) => {
  const parsed = parseFloors(content.floorSelections, FLOOR_RULE_TABLE.categories[category])
  const admission = admitFloors(parsed, stage)
  return [
    ...(admission.ok ? admission.boundaryViolations : admission.violations),
    ...floorReferenceViolations(parsed, floorReferences).map((violation) =>
      floorReferenceError(violation, egoGiftI18n),
    ),
  ]
}

const CHECKS: Record<ContentStage, readonly PlannerCheck[]> = {
  draft: [equippedEntities, deploymentOrder, skillEAState, giftIds, startBuffs, startGift, floors],
  publish: [
    equippedEntities,
    deploymentOrder,
    skillEAState,
    giftIds,
    startBuffs,
    startGift,
    keywords,
    floors,
  ],
}

type PlanInput = {
  stage: ContentStage
  content: unknown
  category: MDCategory
  season: SeasonReferenceData
  egoGiftSpec: Record<string, EGOGiftSpec>
  egoGiftI18n: Record<string, string> | undefined
  registry: PlannerIdRegistry
}

const planOf = (input: PlanInput, content: PlannerContentValue): PlannerPlan => ({
  stage: input.stage,
  content,
  category: input.category,
  references: referenceData(input.egoGiftSpec, input.registry),
  season: input.season,
  floorReferences: floorReferenceData(input.egoGiftSpec, input.registry),
  egoGiftI18n: input.egoGiftI18n ?? {},
})

function contentViolations(input: PlanInput): PlannerValidationError[] {
  const parsed = parseContent(input.content)
  if (!parsed.ok) return parsed.error
  const oversized = noteSizeViolations(parsed.value.sectionNotes)
  if (oversized.length > 0) return oversized
  const plan = planOf(input, parsed.value)
  return CHECKS[input.stage].flatMap((check) => check(plan)).sort(byPathThenCode)
}

function parseMetadata(
  stage: ContentStage,
  title: string | undefined,
  registry: PlannerIdRegistry,
): Result<SeasonReferenceData, PlannerValidationError[]> {
  const missingTitle = stage === 'publish' ? titleViolations(title) : []
  if (missingTitle.length > 0) return err(missingTitle)
  const season = seasonReferenceData(registry)
  return season === undefined ? err([unknownVersion()]) : ok(season)
}

function mdViolations(
  planner: MDSaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n: Record<string, string> | undefined,
  registry: PlannerIdRegistry,
): PlannerValidationError[] {
  const stage: ContentStage = planner.metadata.published ? 'publish' : 'draft'
  const season = parseMetadata(stage, planner.metadata.title, registry)
  if (!season.ok) return season.error
  return contentViolations({
    stage,
    content: planner.content,
    category: planner.config.category,
    season: season.value,
    egoGiftSpec,
    egoGiftI18n,
    registry,
  })
}

export function validatePlannerForSync(
  planner: SaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  egoGiftI18n: Record<string, string> | undefined,
  registry: PlannerIdRegistry,
): Result<ValidatedPlanner, PlannerValidationError[]> {
  const errors = isMDPlanner(planner)
    ? mdViolations(planner, egoGiftSpec, egoGiftI18n, registry)
    : []
  return errors.length === 0 ? ok(planner as ValidatedPlanner) : err(errors)
}
