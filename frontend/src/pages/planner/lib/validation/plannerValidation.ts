import { EncodedGiftIdSchema, FLOOR_RULE_TABLE } from '@/shared/gameData'
import type { FloorRuleStage, MDCategory } from '@/shared/gameData'
import { giftDisplayName, hasGiftId } from '@/pages/egoGift'
import type { EGOGiftSpec } from '@/pages/egoGift'
import { getUnaffordableGiftIds } from '../plannerRules'
import { isMDPlanner } from '../../types/PlannerTypes'
import type { MDPlannerContent, SaveablePlanner } from '../../types/PlannerTypes'
import { contentPath, field } from './contentPath'
import type { ContentPath } from './contentPath'
import { keywordViolations, titleViolations } from './document'
import { egoRuleViolations, parseEgos } from './ego/egos'
import type { EgoReferenceData, ParsedEgos } from './ego/egos'
import {
  COMPREHENSIVE_GIFT_IDS_PATH,
  OBSERVATION_GIFT_IDS_PATH,
  giftIdViolations,
  parseGiftIds,
} from './egogift/giftIds'
import type { GiftReferenceData, ParsedGiftIds } from './egogift/giftIds'
import { floorReferenceViolations } from './floor/floorReferences'
import type { FloorReferenceData, FloorReferenceViolation } from './floor/floorReferences'
import { admitFloors, parseFloors } from './floor/floorRules'
import { identityRuleViolations, parseIdentity } from './identity/identity'
import type { IdentityReferenceData, ParsedIdentity } from './identity/identity'
import type { FloorReferenceError, PlannerValidationError } from './plannerValidationErrors'
import { deploymentOrderViolations } from './sinner/deploymentOrder'
import { parseSinners } from './sinner/sinnerKeys'
import type { ParsedSinners } from './sinner/sinnerKeys'
import { sinnerSkillViolations } from './skill/skillEAState'
import {
  parseStartBuffs,
  startBuffReferenceViolations,
  startBuffViolations,
} from './startbuff/startBuffs'
import type { ParsedStartBuffs, StartBuffReferenceData } from './startbuff/startBuffs'
import { START_GIFT_IDS_PATH, startGiftViolations } from './startgift/startGift'
import { byPathThenCode } from './violation'

export interface PlannerIdRegistry {
  identityIds: ReadonlySet<string>
  egoIds: ReadonlySet<string>
  themePackIds: ReadonlySet<string>
  startBuffIds: ReadonlySet<string>
}

export type PlannerValidationResult = { isValid: boolean; errors: PlannerValidationError[] }

type ContentStage = Exclude<FloorRuleStage, 'index'>

type ReferenceData = IdentityReferenceData &
  EgoReferenceData &
  StartBuffReferenceData &
  GiftReferenceData

type PlannerPlan = {
  stage: ContentStage
  title: string | undefined
  content: MDPlannerContent
  category: MDCategory
  equipment: ParsedSinners
  identities: readonly ParsedIdentity[]
  egos: readonly ParsedEgos[]
  skillEAState: ParsedSinners
  startGifts: ParsedGiftIds
  observationGifts: ParsedGiftIds
  comprehensiveGifts: ParsedGiftIds
  startBuffs: ParsedStartBuffs
  references: ReferenceData
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
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined,
  registry: PlannerIdRegistry | undefined,
): ReferenceData {
  return {
    hasIdentity: (identityId) => registry === undefined || registry.identityIds.has(identityId),
    hasEgo: (egoId) => registry === undefined || registry.egoIds.has(egoId),
    hasStartBuff: (buffId) => registry === undefined || registry.startBuffIds.has(buffId),
    hasEgoGift: (giftId) => egoGiftSpec === undefined || knownGift(giftId, egoGiftSpec),
  }
}

function floorReferenceData(
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined,
  registry: PlannerIdRegistry | undefined,
): FloorReferenceData {
  return {
    hasThemePack: (themePackId) => registry === undefined || registry.themePackIds.has(themePackId),
    hasEgoGift: (giftId) => egoGiftSpec === undefined || knownGift(giftId, egoGiftSpec),
    isGiftAffordableForThemePack: (giftId, themePackId) =>
      egoGiftSpec === undefined || affordableGift(giftId, themePackId, egoGiftSpec),
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

const sorted = (violations: PlannerValidationError[]) => violations.sort(byPathThenCode)

const title: PlannerCheck = (plan) => titleViolations(plan.title)

const equipment: PlannerCheck = ({ equipment: sinners, identities, egos }) =>
  sorted([
    ...sinners.violations,
    ...identities.flatMap((identity) => identity.violations),
    ...egos.flatMap((ego) => ego.violations),
  ])

const equippedEntities: PlannerCheck = ({ identities, egos, startBuffs, references }) =>
  sorted([
    ...identities.flatMap((identity) => identityRuleViolations(identity, references)),
    ...egos.flatMap((ego) => egoRuleViolations(ego, references)),
    ...startBuffReferenceViolations(startBuffs, references),
  ])

const deploymentOrder: PlannerCheck = ({ content }) =>
  deploymentOrderViolations(content.deploymentOrder)

const skillEAState: PlannerCheck = ({ skillEAState: sinners }) =>
  sorted([
    ...sinners.violations,
    ...sinners.entries.flatMap((entry) => sinnerSkillViolations(entry.value, entry.path)),
  ])

const giftIds: PlannerCheck = ({
  startGifts,
  observationGifts,
  comprehensiveGifts,
  references,
}) => [
  ...giftIdViolations(startGifts, 'DUPLICATE_STARTGIFT', references),
  ...giftIdViolations(observationGifts, 'DUPLICATE_EGOGIFT', references),
  ...giftIdViolations(comprehensiveGifts, 'DUPLICATE_EGOGIFT', references),
]

const startBuffs: PlannerCheck = (plan) => startBuffViolations(plan.startBuffs)

const startGift: PlannerCheck = ({ content, startGifts }) =>
  startGiftViolations(content.selectedGiftKeyword, startGifts.elementCount)

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
  draft: [
    equipment,
    equippedEntities,
    deploymentOrder,
    skillEAState,
    giftIds,
    startBuffs,
    startGift,
    floors,
  ],
  publish: [
    title,
    equipment,
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
  title: string | undefined
  content: MDPlannerContent
  category: MDCategory
  egoGiftSpec: Record<string, EGOGiftSpec> | undefined
  egoGiftI18n: Record<string, string> | undefined
  registry: PlannerIdRegistry | undefined
}

function planOf(input: PlanInput): PlannerPlan {
  const { content, egoGiftSpec, registry } = input
  const equipmentEntries = parseSinners(content.equipment, EQUIPMENT_PATH)
  return {
    stage: input.stage,
    title: input.title,
    content,
    category: input.category,
    equipment: equipmentEntries,
    identities: equipmentEntries.entries.map((entry) =>
      parseIdentity(entry.value, entry.path, entry.sinnerKey),
    ),
    egos: equipmentEntries.entries.map((entry) =>
      parseEgos(entry.value, entry.path, entry.sinnerKey),
    ),
    skillEAState: parseSinners(content.skillEAState, SKILL_EA_STATE_PATH),
    startGifts: parseGiftIds(content.selectedGiftIds, START_GIFT_IDS_PATH),
    observationGifts: parseGiftIds(content.observationGiftIds, OBSERVATION_GIFT_IDS_PATH),
    comprehensiveGifts: parseGiftIds(content.comprehensiveGiftIds, COMPREHENSIVE_GIFT_IDS_PATH),
    startBuffs: parseStartBuffs(content.selectedBuffIds),
    references: referenceData(egoGiftSpec, registry),
    floorReferences: floorReferenceData(egoGiftSpec, registry),
    egoGiftI18n: input.egoGiftI18n ?? {},
  }
}

function validateContent(input: PlanInput): PlannerValidationResult {
  const plan = planOf(input)
  const errors = CHECKS[input.stage].flatMap((check) => check(plan))
  return { isValid: errors.length === 0, errors }
}

export function validatePlannerForPublish(
  title: string | undefined,
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): PlannerValidationResult {
  return validateContent({
    stage: 'publish',
    title,
    content,
    category,
    egoGiftSpec,
    egoGiftI18n,
    registry,
  })
}

export function validatePlannerForDraftSave(
  content: MDPlannerContent,
  category: MDCategory,
  egoGiftSpec?: Record<string, EGOGiftSpec>,
  egoGiftI18n?: Record<string, string>,
  registry?: PlannerIdRegistry,
): PlannerValidationResult {
  return validateContent({
    stage: 'draft',
    title: undefined,
    content,
    category,
    egoGiftSpec,
    egoGiftI18n,
    registry,
  })
}

export function validatePlannerForImport(
  planner: SaveablePlanner,
  egoGiftSpec: Record<string, EGOGiftSpec>,
  registry: PlannerIdRegistry,
): PlannerValidationResult {
  if (!isMDPlanner(planner)) return { isValid: true, errors: [] }
  return validateContent({
    stage: planner.metadata.published ? 'publish' : 'draft',
    title: planner.metadata.title,
    content: planner.content,
    category: planner.config.category,
    egoGiftSpec,
    egoGiftI18n: undefined,
    registry,
  })
}
