import fs from 'node:fs'
import path from 'node:path'
import { describe, it, expect } from 'vitest'

const VALIDATION_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..')
const TESTS_DIR = '__tests__'
const CALLER_SPECIFIER = /(^|\/)(hooks|components|stores)(\/|$)|^react(\/|$)|^@\/lib\/api/

const subjects = fs
  .readdirSync(VALIDATION_DIR, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name !== TESTS_DIR)
  .map((entry) => entry.name)

const sourcesOf = (subject: string): string[] =>
  fs
    .readdirSync(path.join(VALIDATION_DIR, subject), { recursive: true, encoding: 'utf-8' })
    .filter((file) => file.endsWith('.ts') && !file.split(path.sep).includes(TESTS_DIR))
    .map((file) => fs.readFileSync(path.join(VALIDATION_DIR, subject, file), 'utf-8'))

const importSpecifiers = (source: string): string[] =>
  [...source.matchAll(/\bfrom\s+'([^']+)'/g)].map((match) => match[1] ?? '')

const leavesSubject = (subject: string, specifier: string): boolean =>
  subjects.some((other) => other !== subject && specifier.startsWith(`../${other}/`)) ||
  specifier.startsWith('../../') ||
  CALLER_SPECIFIER.test(specifier)

describe('validation subject modules', () => {
  it('are the subject folders of the backend package plan', () => {
    expect([...subjects].sort()).toEqual(
      ['ego', 'egogift', 'floor', 'identity', 'sinner', 'skill', 'startbuff', 'startgift'].sort(),
    )
  })

  it.each(subjects)('%s imports only the validation root and shared modules', (subject) => {
    const outside = sourcesOf(subject)
      .flatMap(importSpecifiers)
      .filter((specifier) => leavesSubject(subject, specifier))

    expect(outside).toEqual([])
  })

  it('recognises each import it guards against', () => {
    expect(
      [
        '../identity/identity',
        '../../plannerRules',
        '../../hooks/usePlannerSave',
        'react',
        '@/lib/api',
        '../contentPath',
        '@/shared/gameData',
      ].map((specifier) => leavesSubject('ego', specifier)),
    ).toEqual([true, true, true, true, true, false, false])
  })
})
