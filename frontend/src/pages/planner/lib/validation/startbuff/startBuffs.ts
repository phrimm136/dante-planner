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
  | {
      code: Codes<'EXCESS_STARTBUFF' | 'OUT_OF_RANGE_STARTBUFF' | 'DUPLICATE_STARTBUFF'>
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_STARTBUFF'>; path: ContentPath; params: { id: string } }

type SelectedStartBuff = { buffId: number; path: ContentPath }

export type StartBuffSeason = { hasStartBuff: (buffId: string) => boolean }

const baseIdOf = (buff: SelectedStartBuff) => buff.buffId % BASE_ID_MODULUS

const hasBaseIdInRange = (buff: SelectedStartBuff) =>
  baseIdOf(buff) >= MIN_BUFF_BASE_ID && baseIdOf(buff) <= MAX_BUFF_BASE_ID

const excessViolations = (buffCount: number): StartBuffViolation[] =>
  buffCount > MAX_START_BUFFS ? [{ code: 'EXCESS_STARTBUFF', path: START_BUFFS_PATH }] : []

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
  buffIds: readonly number[],
  season: StartBuffSeason,
): StartBuffViolation[] {
  const buffs = buffIds.map((buffId, position) => ({
    buffId,
    path: extendPath(START_BUFFS_PATH, index(position)),
  }))
  const isKnown = (buff: SelectedStartBuff) => season.hasStartBuff(String(buff.buffId))
  return [
    ...excessViolations(buffs.length),
    ...buffs.filter((buff) => !isKnown(buff)).map(unknownBuff),
    ...knownBuffViolations(buffs.filter(isKnown)),
  ].sort(byPathThenCode)
}
