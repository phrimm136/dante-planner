import { describe, it, expect } from 'vitest'
import { parseColorTags, stripColorTags } from '../ColoredText'

describe('parseColorTags', () => {
  it('returns plain text as-is', () => {
    const result = parseColorTags('Hello world')
    expect(result).toHaveLength(1)
    expect(result[0]).toBe('Hello world')
  })

  it('parses single color tag', () => {
    const result = parseColorTags('<color=#ff0000>red text</color>')
    expect(result).toHaveLength(1)
    // The result is a React element with color style
    const el = result[0] as React.ReactElement
    expect(el.props.style.color).toBe('#ff0000')
  })

  it('preserves text before and after color tag', () => {
    const result = parseColorTags('before <color=#00ff00>green</color> after')
    expect(result).toHaveLength(3)
    expect(result[0]).toBe('before ')
    expect(result[2]).toBe(' after')
  })

  it('handles nested color tags', () => {
    const result = parseColorTags(
      '<color=#ff0000>outer <color=#00ff00>inner</color> still outer</color>'
    )
    expect(result).toHaveLength(1)
    const outer = result[0] as React.ReactElement
    expect(outer.props.style.color).toBe('#ff0000')
  })

  it('handles multiple sequential color tags', () => {
    const result = parseColorTags(
      '<color=#ff0000>red</color> <color=#00ff00>green</color>'
    )
    expect(result).toHaveLength(3)
  })

  it('sanitizes malformed </color=#hex> close tags', () => {
    const result = parseColorTags(
      '<color=#ff0000>text</color> </color=#ebcaa2>more</color>'
    )
    // Should not crash or produce raw tag text
    const flatText = result.map((r) =>
      typeof r === 'string' ? r : ''
    ).join('')
    expect(flatText).not.toContain('</color=')
  })

  it('converts <size> tags to small spans', () => {
    const result = parseColorTags('<size=75%>small text</size>')
    // The sanitize converts <size> to <small>, then ColoredText handles it
    // parseColorTags only does sanitize + parse, <small> stays as string
    const text = result.join('')
    expect(text).not.toContain('<size')
  })

  it('handles empty string', () => {
    const result = parseColorTags('')
    expect(result).toHaveLength(0)
  })

  it('handles text with no matching close tag gracefully', () => {
    // Unclosed tag — parser should not crash
    const result = parseColorTags('<color=#ff0000>unclosed')
    expect(result).toBeDefined()
  })
})

describe('stripColorTags', () => {
  it('strips single color tag', () => {
    expect(stripColorTags('<color=#ff0000>red</color>')).toBe('red')
  })

  it('strips nested color tags', () => {
    expect(stripColorTags(
      '<color=#ff0000>outer <color=#00ff00>inner</color></color>'
    )).toBe('outer inner')
  })

  it('preserves plain text', () => {
    expect(stripColorTags('no tags here')).toBe('no tags here')
  })

  it('handles multiple sequential tags', () => {
    expect(stripColorTags(
      '<color=#ff0000>a</color> <color=#00ff00>b</color>'
    )).toBe('a b')
  })

  it('handles empty string', () => {
    expect(stripColorTags('')).toBe('')
  })

  it('strips deeply nested tags', () => {
    expect(stripColorTags(
      '<color=#aa0000><color=#bb0000><color=#cc0000>deep</color></color></color>'
    )).toBe('deep')
  })
})
