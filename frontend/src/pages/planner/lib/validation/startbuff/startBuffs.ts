import { invalidFieldType, isInteger, parseList } from '../boundary'
import type { InvalidFieldType } from '../boundary'
import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

const MAX_START_BUFFS = 10
const BASE_ID_MODULUS = 100
const MIN_BUFF_BASE_ID = 0
const MAX_BUFF_BASE_ID = 9

const START_BUFFS_PATH = contentPath(field('selectedBuffIds'))

export type StartBuffViolation =
  | InvalidFieldType
  | {
      code: Codes<'EXCESS_STARTBUFF' | 'OUT_OF_RANGE_STARTBUFF' | 'DUPLICATE_STARTBUFF'>
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_STARTBUFF'>; path: ContentPath; params: { id: string } }

export type SelectedStartBuff = { buffId: number; path: ContentPath }

export type ParsedStartBuffs = {
  elementCount: number
  buffs: readonly SelectedStartBuff[]
  violations: readonly InvalidFieldType[]
}

export type StartBuffReferenceData = { hasStartBuff: (buffId: string) => boolean }

type ElementResult = { buffs: SelectedStartBuff[]; violations: InvalidFieldType[] }

function parseElement(value: unknown, path: ContentPath): ElementResult {
  return isInteger(value)
    ? { buffs: [{ buffId: value, path }], violations: [] }
    : { buffs: [], violations: [invalidFieldType(path)] }
}

export function parseStartBuffs(raw: unknown): ParsedStartBuffs {
  const list = parseList(raw, START_BUFFS_PATH)
  const elements = list.elements.map((value, position) =>
    parseElement(value, extendPath(START_BUFFS_PATH, index(position))),
  )
  return {
    elementCount: list.elements.length,
    buffs: elements.flatMap((element) => element.buffs),
    violations: [...list.violations, ...elements.flatMap((element) => element.violations)],
  }
}

const baseIdOf = (buff: SelectedStartBuff) => buff.buffId % BASE_ID_MODULUS

const hasBaseIdInRange = (buff: SelectedStartBuff) =>
  baseIdOf(buff) >= MIN_BUFF_BASE_ID && baseIdOf(buff) <= MAX_BUFF_BASE_ID

const excessViolations = (parsed: ParsedStartBuffs): StartBuffViolation[] =>
  parsed.elementCount > MAX_START_BUFFS
    ? [{ code: 'EXCESS_STARTBUFF', path: START_BUFFS_PATH }]
    : []

function duplicateBaseIdViolations(buffs: readonly SelectedStartBuff[]): StartBuffViolation[] {
  const firstIndex = firstIndexOf(buffs.map(baseIdOf))
  return buffs
    .filter((buff, buffIndex) => firstIndex.get(baseIdOf(buff)) !== buffIndex)
    .map((buff) => ({ code: 'DUPLICATE_STARTBUFF', path: buff.path }))
}

export function startBuffViolations(parsed: ParsedStartBuffs): StartBuffViolation[] {
  const outOfRange = parsed.buffs
    .filter((buff) => !hasBaseIdInRange(buff))
    .map((buff): StartBuffViolation => ({ code: 'OUT_OF_RANGE_STARTBUFF', path: buff.path }))
  return [
    ...parsed.violations,
    ...excessViolations(parsed),
    ...outOfRange,
    ...duplicateBaseIdViolations(parsed.buffs.filter(hasBaseIdInRange)),
  ].sort(byPathThenCode)
}

export const startBuffReferenceViolations = (
  parsed: ParsedStartBuffs,
  data: StartBuffReferenceData,
): StartBuffViolation[] =>
  parsed.buffs
    .filter((buff) => !data.hasStartBuff(String(buff.buffId)))
    .map((buff) => ({
      code: 'UNKNOWN_STARTBUFF',
      path: buff.path,
      params: { id: String(buff.buffId) },
    }))
