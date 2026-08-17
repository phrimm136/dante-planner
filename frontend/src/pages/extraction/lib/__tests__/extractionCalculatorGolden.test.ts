import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import * as calculator from '../extractionCalculator'
import golden from './__fixtures__/extractionCalculatorGolden.json'
import { encodeScalar, MATRIX_GROUP_NAMES, runMatrix, type MatrixScalar } from './extractionMatrix'

/**
 * Characterization test: every calculator entry point driven over the `core`
 * matrix of `extractionMatrix.ts` and compared, field by field and bit for bit,
 * against pinned values.
 *
 * The probabilities are floating point, so any reordering of the arithmetic
 * shows up here as a changed last digit rather than staying invisible.
 *
 * Run with `UPDATE_EXTRACTION_GOLDEN=1` to rewrite the fixture from the current
 * implementation; the diff then has to be reviewed digit by digit.
 */

const FIXTURE_PATH = resolve(
  process.cwd(),
  'src/pages/extraction/lib/__tests__/__fixtures__/extractionCalculatorGolden.json',
)

const expected = golden as Record<string, MatrixScalar[][]>
const actual = runMatrix(calculator, 'core')

if (process.env.UPDATE_EXTRACTION_GOLDEN) {
  const regenerated = Object.fromEntries(
    Object.entries(actual).map(([name, cases]) => [
      name,
      cases.map((c) => c.values.map(encodeScalar)),
    ]),
  )
  writeFileSync(FIXTURE_PATH, `${JSON.stringify(regenerated)}\n`)
}

describe('extractionCalculator golden matrix', () => {
  it('pins every entry point the matrix drives', () => {
    expect(Object.keys(actual).sort()).toEqual(Object.keys(expected).sort())
  })

  describe.each(MATRIX_GROUP_NAMES)('%s', (group) => {
    it('matches the pinned values case for case', () => {
      const cases = actual[group]
      const pinned = expected[group]
      if (pinned === undefined) {
        throw new Error(`golden fixture has no group ${group}`)
      }

      expect(cases.length, `${group} case count`).toBe(pinned.length)

      for (const [i, matrixCase] of cases.entries()) {
        expect(matrixCase.values.map(encodeScalar), `${group}[${i}] ${matrixCase.label}`).toEqual(
          pinned[i],
        )
      }
    })
  })
})
