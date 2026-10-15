import { describe, expect, it } from 'vitest'

import {
  scanTmpRuns,
  tmpPlainText,
  type TmpBreak,
  type TmpRun,
  type TmpToken,
} from '../tmpRichText'
import readings from './__fixtures__/plainTextReadings.json'

function run(text: string, state: Partial<Omit<TmpRun, 'kind' | 'text'>> = {}): TmpRun {
  return { kind: 'run', text, bold: false, underline: false, strike: false, ...state }
}

const br: TmpBreak = { kind: 'break' }

const ORANGE_MARK = { rgb: '#ff0000', alphaPct: '25.098%' }

interface Case {
  rule: string
  source: string
  input: string
  expected: TmpToken[]
}

const cases: Case[] = [
  {
    rule: '#RRGGBB colour, case-insensitive hex',
    source: 'TMP_Text.cs:7650-7655, 6511-6517',
    input: '<color=#FF6000>a</color>b',
    expected: [run('a', { color: '#ff6000' }), run('b')],
  },
  {
    rule: '#RGB colour',
    source: 'TMP_Text.cs:7636-7641, 6490-6497',
    input: '<color=#f60>a',
    expected: [run('a', { color: '#ff6600' })],
  },
  {
    rule: '#RGBA colour',
    source: 'TMP_Text.cs:7643-7648, 6499-6507',
    input: '<color=#f608>a',
    expected: [run('a', { color: '#ff660088' })],
  },
  {
    rule: '#RRGGBBAA colour',
    source: 'TMP_Text.cs:7657-7662, 6518-6526',
    input: '<color=#ff600080>a',
    expected: [run('a', { color: '#ff600080' })],
  },
  {
    rule: 'shorthand <#hex> pushes the colour stack',
    source: 'TMP_Text.cs:6931-6957, 7819-7821',
    input: '<#ff0000>a</color>b',
    expected: [run('a', { color: '#ff0000' }), run('b')],
  },
  {
    rule: 'named colours, case-insensitive; yellow is Unity Color.yellow',
    source: 'TMP_Text.cs:7664-7707, 6793; UnityCsReference Color.cs:783, 873',
    input: '<color=RED>a</color><color=Yellow>b<color=orange>c<color=purple>d',
    expected: [
      run('a', { color: '#ff0000' }),
      run('b', { color: '#ffeb04' }),
      run('c', { color: '#ff8000' }),
      run('d', { color: '#a020f0' }),
    ],
  },
  {
    rule: 'lightblue and grey never match: their constants hash lower case, values hash upper case',
    source: 'TMP_Text.cs:7672, 7680, 6793; TMP_TextUtilities.cs:2183-2206',
    input: '<color=lightblue>a<color=grey>b',
    expected: [run('<color=lightblue>a<color=grey>b')],
  },
  {
    rule: 'an empty or unmatched colour value is literal text',
    source: 'TMP_Text.cs:7708-7709; TMPro_UGUI_Private.cs:1971-1988',
    input: '<color=>x</color><color=#ff000>y',
    expected: [run('<color=>x<color=#ff000>y')],
  },
  {
    rule: 'a non-hex digit reads as 15',
    source: 'TMP_Text.cs:6379-6406',
    input: '<color=#zz0000>a',
    expected: [run('a', { color: '#ff0000' })],
  },
  {
    rule: 'colour stack: close pops, a stray close at the base is a no-op',
    source: 'TMP_Text.cs:7819-7821; TMP_TextProcessingStack.cs:302-316',
    input: '</color>a<color=#aa0000>b<color=#bb0000>c</color>d</color></color>e',
    expected: [
      run('a'),
      run('b', { color: '#aa0000' }),
      run('c', { color: '#bb0000' }),
      run('d', { color: '#aa0000' }),
      run('e'),
    ],
  },
  {
    rule: '</color=#hex> is a colour close: the name ends at "="',
    source: 'TMP_Text.cs:6880-6896, 7819-7821',
    input: '<color=#ff0000>a</color=#ebcaa2>b',
    expected: [run('a', { color: '#ff0000' }), run('b')],
  },
  {
    rule: 'b counter; a stray close at zero is a no-op',
    source: 'TMP_Text.cs:6966-6980; TMP_TextProcessingStack.cs:40-46, 76-85',
    input: '</b><b><b>a</b>b</b>c</b>d',
    expected: [run('ab', { bold: true }), run('cd')],
  },
  {
    rule: 'u counter; the underline colour is the colour current at the open',
    source: 'TMP_Text.cs:7032-7054; TMPro_UGUI_Private.cs:2206',
    input: '<color=#ff6000><u>a</color>b</u>c',
    expected: [
      run('a', { color: '#ff6000', underline: true, decorationColor: '#ff6000' }),
      run('b', { underline: true, decorationColor: '#ff6000' }),
      run('c'),
    ],
  },
  {
    rule: '<u color=#hex> sets the underline colour',
    source: 'TMP_Text.cs:7036-7040, 6570-6588',
    input: '<u color=#00ff00>a',
    expected: [run('a', { underline: true, decorationColor: '#00ff00' })],
  },
  {
    rule: 's counter with its colour stack; crossing colour closes do not move it',
    source: 'TMP_Text.cs:7008-7031; TMPro_UGUI_Private.cs:2207',
    input: '<mark=#000000AA>- <color=#ff9000><s></color>T<color=#ff0000></s></color></mark>',
    expected: [
      run('- ', { mark: { rgb: '#000000', alphaPct: '66.667%' } }),
      run('T', {
        mark: { rgb: '#000000', alphaPct: '66.667%' },
        strike: true,
        decorationColor: '#ff9000',
      }),
    ],
  },
  {
    rule: 'mark stack: the innermost mark is drawn, close pops',
    source: 'TMP_Text.cs:7056-7104',
    input: '<mark=#ff000040>a<mark=#0000ff80>b</mark>c</mark>d',
    expected: [
      run('a', { mark: ORANGE_MARK }),
      run('b', { mark: { rgb: '#0000ff', alphaPct: '50.196%' } }),
      run('c', { mark: ORANGE_MARK }),
      run('d'),
    ],
  },
  {
    rule: '<mark> without a colour is yellow at 64',
    source: 'TMP_Text.cs:7060',
    input: '<mark>a',
    expected: [run('a', { mark: { rgb: '#ffff00', alphaPct: '25.098%' } })],
  },
  {
    rule: '<mark color=#hex> is the same mark',
    source: 'TMP_Text.cs:7064-7077',
    input: '<mark color=#ff000040>a',
    expected: [run('a', { mark: ORANGE_MARK })],
  },
  {
    rule: 'the mark alpha is capped by the current colour alpha',
    source: 'TMP_Text.cs:7090',
    input: '<color=#ff600080><mark=#ff0000ff>a',
    expected: [run('a', { color: '#ff600080', mark: { rgb: '#ff0000', alphaPct: '50.196%' } })],
  },
  {
    rule: 'size stack in % and em',
    source: 'TMP_Text.cs:7262-7301, 6800-6817',
    input: '<size=95%>a<size=1.5em>b</size>c</size>d',
    expected: [
      run('a', { fontSize: '95%' }),
      run('b', { fontSize: '1.5em' }),
      run('c', { fontSize: '95%' }),
      run('d'),
    ],
  },
  {
    rule: '[I] a pixel size is consumed but not mapped to CSS',
    source: 'TMP_Text.cs:7268-7288',
    input: 'a<size=+2>b</size>',
    expected: [run('ab')],
  },
  {
    rule: 'an empty size value is literal text',
    source: 'TMP_Text.cs:6644-6650, 7266',
    input: '<size=>a',
    expected: [run('<size=>a')],
  },
  {
    rule: '[I] upgradeHighlight pushes the enhanced colour until </style>',
    source: 'TMP_Text.cs:2306-2338, 3009-3029',
    input: '<style="upgradeHighlight">a</style>b',
    expected: [run('a', { color: '#f8c200' }), run('b')],
  },
  {
    rule: '[I] any other style name is consumed without styling',
    source: 'TMP_Text.cs:2306-2338, 3221-3238',
    input: '<style="OrangeMark">a</style>b</style>',
    expected: [run('ab')],
  },
  {
    rule: 'noparse keeps every tag but its close literal',
    source: 'TMP_Text.cs:6921-6929, 8199-8201, 2247-2252',
    input: '<noparse><b>a</b><br></noparse>b',
    expected: [run('<b>a</b><br>b')],
  },
  {
    rule: '<br> is a line break',
    source: 'TMP_Text.cs:2253-2260',
    input: 'a<br>b',
    expected: [run('a'), br, run('b')],
  },
  {
    rule: 'state carries across a line break',
    source: 'TMPro_UGUI_Private.cs:1836-1848',
    input: '<b>a\nb</b>',
    expected: [run('a', { bold: true }), br, run('b', { bold: true })],
  },
  {
    rule: '[I] escaped \\n breaks and \\\\ is one backslash (parseCtrlCharacters on)',
    source: 'TMP_Text.cs:957, 2172-2187',
    input: 'a\\nb\\\\nc',
    expected: [run('a'), br, run('b\\nc')],
  },
  {
    rule: 'an unknown tag is literal text',
    source: 'TMP_Text.cs:8336; TMPro_UGUI_Private.cs:1971-1988',
    input: '<Bloodfiend> gains <La Manchaland>',
    expected: [run('<Bloodfiend> gains <La Manchaland>')],
  },
  {
    rule: 'a "<" inside a tag ends it unread',
    source: 'TMP_Text.cs:6748',
    input: '<color=#ff0000<b>a',
    expected: [run('<color=#ff0000'), run('a', { bold: true })],
  },
  {
    rule: 'a second bare attribute rejects the tag',
    source: 'TMP_Text.cs:6884-6887',
    input: '<b c d>x',
    expected: [run('<b c d>x')],
  },
  {
    rule: 'a tag longer than 127 characters is literal text',
    source: 'TMP_Text.cs:1539, 6748',
    input: `<size=${'0'.repeat(119)}95%>a<size=${'0'.repeat(120)}95%>`,
    expected: [run(`a<size=${'0'.repeat(120)}95%>`, { fontSize: '95%' })],
  },
  {
    rule: '<i> is consumed without italic (D5)',
    source: 'TMP_Text.cs:6982-7007',
    input: '<i>a</i>',
    expected: [run('a')],
  },
]

describe('scanTmpRuns', () => {
  it.each(cases)('$rule ($source)', ({ input, expected }) => {
    expect(scanTmpRuns(input)).toEqual(expected)
  })

  it('reads an OrangeMark run as per-tag state', () => {
    expect(
      scanTmpRuns(
        'On Clash Lose, <color=#ff6000><mark=#ff000040><b><u>gain [Sinking] 2</u></b></mark></color> next',
      ),
    ).toEqual([
      run('On Clash Lose, '),
      run('gain [Sinking] 2', {
        color: '#ff6000',
        mark: ORANGE_MARK,
        bold: true,
        underline: true,
        decorationColor: '#ff6000',
      }),
      run(' next'),
    ])
  })

  it('keeps a doubled <u> underlined until its second close', () => {
    expect(scanTmpRuns('<u><u>A</u>B</u>C')).toEqual([run('AB', { underline: true }), run('C')])
  })

  it('returns nothing for empty text', () => {
    expect(scanTmpRuns('')).toEqual([])
  })
})

describe('tmpPlainText', () => {
  it.each([...readings.unitKeywordLabels, ...readings.themePackNames])(
    'reads shipped label $input as the grammar reader did',
    ({ input, plain }) => {
      expect(tmpPlainText(input)).toBe(plain)
    },
  )

  it('reads a line break as a space', () => {
    expect(tmpPlainText('<color=#ff0000>a</color>\nb<br>c')).toBe('a b c')
  })
})
