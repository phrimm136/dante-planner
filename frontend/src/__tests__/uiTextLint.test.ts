/// <reference types="node" />
import { spawnSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { describe, it, expect } from 'vitest'

const FRONTEND_ROOT = resolve(dirname(new URL(import.meta.url).pathname), '../..')
const OXLINT_BIN = join(FRONTEND_ROOT, 'node_modules/.bin/oxlint')
const FIXTURE_DIR = 'lint/__fixtures__/uiText'
const RULE_CODE = 'ui-text(no-hardcoded-text)'
const REPORTED_TEXT = /^Hardcoded UI text "(?<text>.*)": \d+ found/

type Diagnostic = {
  message: string
  code?: string
  filename: string
  labels: { span: { line: number } }[]
}

type LintRun = {
  status: number | null
  output: string
}

function runOxlint(config: string): LintRun {
  const result = spawnSync(
    OXLINT_BIN,
    ['-c', `${FIXTURE_DIR}/${config}`, '--format', 'json', FIXTURE_DIR],
    { cwd: FRONTEND_ROOT, encoding: 'utf8' },
  )
  if (result.error) throw result.error
  return { status: result.status, output: `${result.stdout}${result.stderr}` }
}

function diagnosticsOf(run: LintRun): readonly Diagnostic[] {
  return (JSON.parse(run.output) as { diagnostics: Diagnostic[] }).diagnostics
}

function ruleDiagnostics(
  diagnostics: readonly Diagnostic[],
  fixture: string,
): readonly Diagnostic[] {
  return diagnostics.filter(
    (diagnostic) =>
      diagnostic.filename.endsWith(`uiText/${fixture}`) && diagnostic.code === RULE_CODE,
  )
}

function reportedTexts(diagnostics: readonly Diagnostic[], fixture: string): readonly string[] {
  return ruleDiagnostics(diagnostics, fixture).map(
    (diagnostic) => diagnostic.message.match(REPORTED_TEXT)?.groups?.text ?? diagnostic.message,
  )
}

describe('ui-text lint plugin', () => {
  const diagnostics = diagnosticsOf(runOxlint('oxlintrc.json'))

  it('reports JSX text, restricted attributes and conditional branches', () => {
    expect([...reportedTexts(diagnostics, 'Bad.tsx')].sort()).toEqual(
      ['Close', 'Slash', 'Defense', 'Attack', 'Lv. ${}', 'Untitled', 'Locked'].sort(),
    )
  })

  it('reports non-JSX label sites', () => {
    expect([...reportedTexts(diagnostics, 'labels.ts')].sort()).toEqual([
      '30 minutes',
      'Loading...',
    ])
  })

  it('reports no error text, SVG path or other non-sink string', () => {
    expect(ruleDiagnostics(diagnostics, 'good.ts')).toEqual([])
    expect(ruleDiagnostics(diagnostics, 'Good.tsx')).toEqual([])
  })

  it('passes a baselined text and fails one more occurrence', () => {
    expect(
      ruleDiagnostics(diagnostics, 'Seen.tsx').map((diagnostic) => diagnostic.message),
    ).toEqual([
      'Hardcoded UI text "Missing": 2 found, baseline allows 1. Translate it with t(); never add it to lint/ui-text-baseline.json.',
    ])
  })

  it('fails a stale baseline entry and names it', () => {
    expect(
      ruleDiagnostics(diagnostics, 'Stale.tsx').map((diagnostic) => ({
        line: diagnostic.labels[0]?.span.line,
        message: diagnostic.message,
      })),
    ).toEqual([
      {
        line: 1,
        message:
          'Stale baseline entry lint/__fixtures__/uiText/Stale.tsx → "Timed Out": baseline allows 2, found 1. Lower or remove it in lint/ui-text-baseline.json.',
      },
    ])
  })

  it('fails loudly on a malformed baseline', () => {
    const run = runOxlint('oxlintrc.malformed.json')

    expect(run.status).not.toBe(0)
    expect(run.output).toContain('ui-text baseline')
    expect(run.output).toContain('invalid')
  })
})
