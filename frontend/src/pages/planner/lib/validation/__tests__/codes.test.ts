import fs from 'node:fs'
import path from 'node:path'
import ts from 'typescript'
import { describe, it, expect } from 'vitest'

import { VALIDATION_CODES } from '../codes'

type VocabularyEntry = { emittedBy: string[] }

const VALIDATION_DIR = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..')
const FRONTEND_ROOT = path.resolve(VALIDATION_DIR, '../../../../..')
const VOCABULARY_PATH = path.resolve(FRONTEND_ROOT, '../testdata/planner-error-codes.json')
const CODES_FILE = path.resolve(VALIDATION_DIR, 'codes.ts')
const TESTS_DIR = '__tests__'
const CODE_LITERAL = /^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/

const vocabulary = JSON.parse(fs.readFileSync(VOCABULARY_PATH, 'utf-8')) as {
  codes: Record<string, VocabularyEntry>
}

const emittingSources = (): string[] =>
  fs
    .readdirSync(VALIDATION_DIR, { recursive: true, encoding: 'utf-8' })
    .filter((file) => file.endsWith('.ts') && !file.split(path.sep).includes(TESTS_DIR))
    .map((file) => path.resolve(VALIDATION_DIR, file))
    .filter((file) => file !== CODES_FILE)

const insideType = (node: ts.Node): boolean =>
  node.parent !== undefined && (ts.isTypeNode(node.parent) || insideType(node.parent))

function codeLiterals(file: string): string[] {
  const source = ts.createSourceFile(
    file,
    fs.readFileSync(file, 'utf-8'),
    ts.ScriptTarget.Latest,
    true,
  )
  const found: string[] = []
  const visit = (node: ts.Node): void => {
    if (ts.isStringLiteral(node) && CODE_LITERAL.test(node.text) && !insideType(node)) {
      found.push(node.text)
    }
    ts.forEachChild(node, visit)
  }
  visit(source)
  return found
}

const sorted = (codes: Iterable<string>) => [...new Set(codes)].sort()

describe('validation code vocabulary', () => {
  it('holds exactly the codes of the shared vocabulary', () => {
    expect(sorted(VALIDATION_CODES)).toEqual(sorted(Object.keys(vocabulary.codes)))
  })

  it('emits exactly the codes the vocabulary assigns to the frontend', () => {
    const assigned = Object.entries(vocabulary.codes)
      .filter(([, entry]) => entry.emittedBy.includes('frontend'))
      .map(([code]) => code)

    expect(sorted(emittingSources().flatMap(codeLiterals))).toEqual(sorted(assigned))
  })
})
