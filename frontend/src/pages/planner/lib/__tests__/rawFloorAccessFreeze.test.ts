/// <reference types="node" />
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const FRONTEND_ROOT = resolve(dirname(new URL(import.meta.url).pathname), '../../../../..')
const AST_GREP_BIN = join(FRONTEND_ROOT, 'node_modules/.bin/ast-grep')
const RULE_DIR = join(FRONTEND_ROOT, 'scripts/ast-grep-rules')
const RULE_FILES = ['no-raw-floor-access.yml', 'no-raw-floor-access-tsx.yml']
const FLOOR_MODULE = 'src/pages/planner/lib/floorRules.ts'
const GLOB_CHARS = /[*?[{]/
const IGNORES_KEY = 'ignores:'
const LIST_ITEM = /^\s+-\s+(.+)$/

function ignoresBlock(lines: readonly string[]): { start: number; end: number } {
  const start = lines.indexOf(IGNORES_KEY)
  let end = start + 1
  while (end < lines.length && LIST_ITEM.test(lines[end] ?? '')) end += 1
  return { start, end }
}

function ignoreEntries(ruleText: string): string[] {
  const lines = ruleText.split('\n')
  const { start, end } = ignoresBlock(lines)
  return lines
    .slice(start + 1, end)
    .map((line) => (line.match(LIST_ITEM)?.[1] ?? '').replace(/^'(.*)'$/, '$1'))
}

function withoutIgnores(ruleText: string): string {
  const lines = ruleText.split('\n')
  const { start, end } = ignoresBlock(lines)
  return [...lines.slice(0, start), ...lines.slice(end)].join('\n')
}

function matchCount(inlineRule: string, sourcePath: string): number {
  let stdout: string
  try {
    stdout = execFileSync(
      AST_GREP_BIN,
      ['scan', '--inline-rules', inlineRule, '--json=compact', sourcePath],
      { cwd: FRONTEND_ROOT, encoding: 'utf8' },
    )
  } catch (error) {
    const failure = error as { stdout?: string }
    if (typeof failure.stdout !== 'string') throw error
    stdout = failure.stdout
  }
  return (JSON.parse(stdout) as unknown[]).length
}

describe.each(RULE_FILES)('%s frozen violators', (ruleFile) => {
  const ruleText = readFileSync(join(RULE_DIR, ruleFile), 'utf8')
  const ignores = ignoreEntries(ruleText)
  const frozen = ignores.filter((entry) => !GLOB_CHARS.test(entry) && entry !== FLOOR_MODULE)

  it('exempts the floor module', () => {
    expect(ignores).toContain(FLOOR_MODULE)
  })

  it('names only files that exist', () => {
    expect(frozen.filter((path) => !existsSync(join(FRONTEND_ROOT, path)))).toEqual([])
  })

  it('names only files that still read floors raw; remove the others from ignores', () => {
    const unfrozen = withoutIgnores(ruleText)
    expect(frozen.filter((path) => matchCount(unfrozen, path) === 0)).toEqual([])
  })
})
