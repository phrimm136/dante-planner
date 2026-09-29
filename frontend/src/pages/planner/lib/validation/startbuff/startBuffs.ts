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
  listViolations: readonly InvalidFieldType[]
  elementViolations: readonly InvalidFieldType[]
}

export type StartBuffSeason = { hasStartBuff: (buffId: string) => boolean }

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
    listViolations: list.violations,
    elementViolations: elements.flatMap((element) => element.violations),
  }
}

const baseIdOf = (buff: SelectedStartBuff) => buff.buffId % BASE_ID_MODULUS

const hasBaseIdInRange = (buff: SelectedStartBuff) =>
  baseIdOf(buff) >= MIN_BUFF_BASE_ID && baseIdOf(buff) <= MAX_BUFF_BASE_ID

const excessViolations = (parsed: ParsedStartBuffs): StartBuffViolation[] =>
  parsed.elementCount > MAX_START_BUFFS
    ? [{ code: 'EXCESS_STARTBUFF', path: START_BUFFS_PATH }]
    : []

const unknownBuff = (buff: SelectedStartBuff): StartBuffViolation => ({
  code: 'UNKNOWN_STARTBUFF',
  path: buff.path,
  params: { id: String(buff.buffId) },
})

function knownBuffViolations(buffs: readonly SelectedStartBuff[]): StartBuffViolation[] {
  const countedBases = buffs.map((buff) => (hasBaseIdInRange(buff) ? baseIdOf(buff) : undefined))
  const firstIndex = firstIndexOf(countedBases)
  return buffs.flatMap((buff, buffIndex): StartBuffViolation[] => {
    const base = countedBases[buffIndex]
    if (base === undefined) return [{ code: 'OUT_OF_RANGE_STARTBUFF', path: buff.path }]
    return firstIndex.get(base) === buffIndex
      ? []
      : [{ code: 'DUPLICATE_STARTBUFF', path: buff.path }]
  })
}

export function startBuffViolations(
  parsed: ParsedStartBuffs,
  season: StartBuffSeason | undefined,
): StartBuffViolation[] {
  if (season === undefined) return [...parsed.listViolations]
  const isKnown = (buff: SelectedStartBuff) => season.hasStartBuff(String(buff.buffId))
  return [
    ...parsed.listViolations,
    ...parsed.elementViolations,
    ...excessViolations(parsed),
    ...parsed.buffs.filter((buff) => !isKnown(buff)).map(unknownBuff),
    ...knownBuffViolations(parsed.buffs.filter(isKnown)),
  ].sort(byPathThenCode)
}
