import { describe, it, expect } from 'vitest'
import { colorRuns } from '../tmpRichText'

describe('colorRuns', () => {
  it('splits text into runs carrying the colour of their innermost tag', () => {
    expect(colorRuns('a <color=#dd0000>b <color=#f8c200>c</color></color> d')).toEqual([
      { text: 'a ', color: undefined },
      { text: 'b ', color: '#dd0000' },
      { text: 'c', color: '#f8c200' },
      { text: ' d', color: undefined },
    ])
  })

  it('keeps a line break inside a coloured run', () => {
    expect(colorRuns('<color=#f8c200>x\ny</color>')).toEqual([{ text: 'x\ny', color: '#f8c200' }])
  })
})
