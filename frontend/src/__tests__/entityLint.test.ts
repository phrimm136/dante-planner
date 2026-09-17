/// <reference types="node" />
import { execFileSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const FRONTEND_ROOT = resolve(dirname(new URL(import.meta.url).pathname), '../..')
const OXLINT_BIN = join(FRONTEND_ROOT, 'node_modules/.bin/oxlint')
const FIXTURE_DIR = 'lint/__fixtures__'

type Diagnostic = {
  message: string
  code: string
  filename: string
}

type Finding = {
  file: string
  rule: string
  name: string
}

const RULE_CODE = /^entity\((?<rule>[\w-]+)\)$/
const REPORTED_NAME = /`(?<name>[^`]+)`/

function runOxlint(): readonly Diagnostic[] {
  let stdout: string
  try {
    stdout = execFileSync(OXLINT_BIN, ['-c', '.oxlintrc.json', '--format', 'json', FIXTURE_DIR], {
      cwd: FRONTEND_ROOT,
      encoding: 'utf8',
    })
  } catch (error) {
    const failure = error as { stdout?: string }
    if (typeof failure.stdout !== 'string') throw error
    stdout = failure.stdout
  }
  return (JSON.parse(stdout) as { diagnostics: Diagnostic[] }).diagnostics
}

function findings(diagnostics: readonly Diagnostic[], fixture: string): readonly Finding[] {
  return diagnostics
    .filter((diagnostic) => diagnostic.filename.endsWith(fixture))
    .map((diagnostic) => ({
      file: fixture,
      rule: diagnostic.code.match(RULE_CODE)?.groups?.rule ?? diagnostic.code,
      name: diagnostic.message.match(REPORTED_NAME)?.groups?.name ?? '',
    }))
    .sort((a, b) => `${a.rule}${a.name}`.localeCompare(`${b.rule}${b.name}`))
}

describe('entity lint plugin', () => {
  const diagnostics = runOxlint()

  it('reports nothing on a conforming entity module', () => {
    expect(findings(diagnostics, 'pages/good/lib/goodEntity.ts')).toEqual([])
  })

  it('reports every violation in a non-conforming entity module', () => {
    expect(findings(diagnostics, 'pages/bad/lib/badEntity.ts')).toEqual([
      { file: 'pages/bad/lib/badEntity.ts', rule: 'no-plural-builder', name: 'toBadEntities' },
      { file: 'pages/bad/lib/badEntity.ts', rule: 'singular-builder', name: 'helper' },
      { file: 'pages/bad/lib/badEntity.ts', rule: 'singular-builder', name: 'toBadEntities' },
      { file: 'pages/bad/lib/badEntity.ts', rule: 'singular-builder', name: 'toBadEntity' },
    ])
  })

  it('reports a plural builder outside the entity modules', () => {
    expect(findings(diagnostics, 'plural.ts')).toEqual([
      { file: 'plural.ts', rule: 'no-plural-builder', name: 'toThingEntities' },
    ])
  })
})
