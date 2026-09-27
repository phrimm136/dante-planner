import { describe, it, expect } from 'vitest'
import { gzipSync } from 'node:zlib'
import { checkEntryBudget } from './entryBudget'

const ENTRY_SRC = '/a/D7C6fpBqIFlb.js'
const ENTRY_HTML = `<!doctype html><html><head>
<script type="module" crossorigin src="${ENTRY_SRC}"></script>
<link rel="modulepreload" crossorigin href="/a/other.js">
</head><body></body></html>`
const ENTRY_BYTES = Buffer.from(
  Array.from({ length: 4000 }, (_, i) => `export const v${i} = ${i * 7919}\n`).join(''),
)
const ENTRY_GZIP_BYTES = gzipSync(ENTRY_BYTES, { level: 9 }).length

const readEntry = (src: string): Buffer => {
  if (src !== ENTRY_SRC) throw new Error(`unexpected read of ${src}`)
  return ENTRY_BYTES
}

describe('checkEntryBudget', () => {
  it('reports the entry file, its gzip size and the budget as not ok when over', () => {
    const budget = ENTRY_GZIP_BYTES - 1

    expect(checkEntryBudget(ENTRY_HTML, readEntry, budget)).toEqual({
      file: ENTRY_SRC,
      gzipBytes: ENTRY_GZIP_BYTES,
      budget,
      ok: false,
    })
  })

  it('is ok when the entry gzip size is within the budget', () => {
    expect(checkEntryBudget(ENTRY_HTML, readEntry, ENTRY_GZIP_BYTES)).toEqual({
      file: ENTRY_SRC,
      gzipBytes: ENTRY_GZIP_BYTES,
      budget: ENTRY_GZIP_BYTES,
      ok: true,
    })
  })

  it('throws a clear error when index.html has no module script', () => {
    const html = '<html><head><script src="/legacy.js"></script></head></html>'

    expect(() => checkEntryBudget(html, readEntry, 1)).toThrow(
      'index.html has no <script type="module" src=…> entry',
    )
  })

  it('throws when index.html has more than one module script', () => {
    const html = `${ENTRY_HTML}<script type="module" src="/a/second.js"></script>`

    expect(() => checkEntryBudget(html, readEntry, 1)).toThrow(
      'index.html has 2 module scripts, expected exactly one entry: /a/D7C6fpBqIFlb.js, /a/second.js',
    )
  })
})
