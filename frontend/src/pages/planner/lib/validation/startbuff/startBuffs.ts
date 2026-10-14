import type { Codes } from '../codes'
import { contentPath, extendPath, field, index } from '../contentPath'
import type { ContentPath } from '../contentPath'
import { firstIndexOf } from '../positions'
import { byPathThenCode } from '../violation'

const BASE_ID_MODULUS = 100

const START_BUFFS_PATH = contentPath(field('selectedBuffIds'))

export type StartBuffViolation =
  | {
      code: Codes<'EXCESS_STARTBUFF' | 'DUPLICATE_STARTBUFF'>
      path: ContentPath
    }
  | { code: Codes<'UNKNOWN_STARTBUFF'>; path: ContentPath; params: { id: string } }

type SelectedStartBuff = { buffId: number; path: ContentPath }

export type StartBuffSeason = {
  hasStartBuff: (buffId: string) => boolean
  startBuffBaseIds: ReadonlySet<number>
}

export const startBuffBaseIds = (startBuffIds: Iterable<string>): ReadonlySet<number> =>
  new Set(Array.from(startBuffIds, (buffId) => Number(buffId) % BASE_ID_MODULUS))

const baseIdOf = (buff: SelectedStartBuff) => buff.buffId % BASE_ID_MODULUS

const excessViolations = (buffCount: number, season: StartBuffSeason): StartBuffViolation[] =>
  buffCount > season.startBuffBaseIds.size
    ? [{ code: 'EXCESS_STARTBUFF', path: START_BUFFS_PATH }]
    : []

const unknownBuff = (buff: SelectedStartBuff): StartBuffViolation => ({
  code: 'UNKNOWN_STARTBUFF',
  path: buff.path,
  params: { id: String(buff.buffId) },
})

function knownBuffViolations(buffs: readonly SelectedStartBuff[]): StartBuffViolation[] {
  const bases = buffs.map(baseIdOf)
  const firstIndex = firstIndexOf(bases)
  return buffs.flatMap((buff, buffIndex): StartBuffViolation[] =>
    firstIndex.get(baseIdOf(buff)) === buffIndex
      ? []
      : [{ code: 'DUPLICATE_STARTBUFF', path: buff.path }],
  )
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
    ...excessViolations(buffs.length, season),
    ...buffs.filter((buff) => !isKnown(buff)).map(unknownBuff),
    ...knownBuffViolations(buffs.filter(isKnown)),
  ].sort(byPathThenCode)
}
