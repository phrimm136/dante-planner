import { describe, it, expect } from 'vitest'

import { diffHtml, normalizeHtml } from '../domDiff'

describe('normalizeHtml', () => {
  it('puts each tag and text node on its own line, indented by depth', () => {
    expect(normalizeHtml('<div class="a"><span>x</span><img src="i.webp"></div>')).toBe(
      [
        '<div class="a">',
        '  <span>',
        '    "x"',
        '  </span>',
        '  <img src="i.webp" />',
        '</div>',
        '',
      ].join('\n'),
    )
  })

  it('orders attributes by name, so attribute order never shows as a change', () => {
    expect(normalizeHtml('<p id="x" class="y" aria-label="z"></p>')).toBe(
      normalizeHtml('<p aria-label="z" class="y" id="x"></p>'),
    )
    expect(normalizeHtml('<p id="x" class="y"></p>')).toBe('<p class="y" id="x" />\n')
  })

  it('keeps whitespace inside text nodes', () => {
    expect(normalizeHtml('<b>a</b> <b>b</b>')).toBe(
      ['<b>', '  "a"', '</b>', '" "', '<b>', '  "b"', '</b>', ''].join('\n'),
    )
  })

  it('keeps comment nodes', () => {
    expect(normalizeHtml('<i><!--x--></i>')).toBe(['<i>', '  <!--x-->', '</i>', ''].join('\n'))
  })
})

describe('diffHtml', () => {
  it('is empty for equal DOMs written in different attribute orders', () => {
    expect(diffHtml('<p id="x" class="y">t</p>', '<p class="y" id="x">t</p>')).toBe('')
  })

  it('prefixes a changed text node with the path of tags that enclose it', () => {
    expect(
      diffHtml(
        '<main><section class="skills wide"><span>3-5</span></section></main>',
        '<main><section class="skills wide"><span>4-7</span></section></main>',
      ),
    ).toBe(
      [
        '@@',
        '- main[1] > section.skills[1] > span[1] #1: "3-5"',
        '+ main[1] > section.skills[1] > span[1] #1: "4-7"',
      ].join('\n'),
    )
  })

  it('reports a changed attribute on the line of its element', () => {
    expect(
      diffHtml(
        '<div><button data-selected="false"></button></div>',
        '<div><button data-selected="true"></button></div>',
      ),
    ).toBe(
      [
        '@@',
        '- div[1] #1: <button data-selected="false" />',
        '+ div[1] #1: <button data-selected="true" />',
      ].join('\n'),
    )
  })

  it('gives a root-level line no path', () => {
    expect(diffHtml('<b>x</b><i />', '<b>x</b>')).toBe(['@@', '- #2: <i />'].join('\n'))
  })

  it('leaves the diff unchanged by a text change or by added lines elsewhere that renumber no ancestor', () => {
    const cell = (header: string, power: string) =>
      `<main><h1>${header}</h1><p>a</p><p>b</p><div class="coin"><span>${power}</span></div></main>`
    const expected = diffHtml(cell('Uptie', '3-5'), cell('Uptie', '4-7'))

    expect(diffHtml(cell('Uptie Tier', '3-5'), cell('Uptie Tier', '4-7'))).toBe(expected)
    expect(
      diffHtml(
        cell('Uptie<b>new</b><i>lines</i>', '3-5'),
        cell('Uptie<b>new</b><i>lines</i>', '4-7'),
      ),
    ).toBe(expected)
  })

  it('numbers a step by its element position, so a sibling inserted before an ancestor shows', () => {
    const cell = (power: string) => `<main><div class="coin"><span>${power}</span></div></main>`

    expect(
      diffHtml(`<header></header>${cell('3-5')}`, `<header></header>${cell('4-7')}`),
    ).toContain('- main[2] > div.coin[1] > span[1] #1: "3-5"')
  })

  it('tells apart the same change made to the first or the second of two identical siblings', () => {
    const passives = (first: string, second: string) =>
      `<div class="passives"><div class="relative ${first}"><span>Locked</span></div>` +
      `<div class="relative ${second}"><span>Locked</span></div></div>`
    const locked = passives('opacity-50', 'opacity-50')

    const unlockFirst = diffHtml(locked, passives('', 'opacity-50'))
    const unlockSecond = diffHtml(locked, passives('opacity-50', ''))

    expect(unlockFirst).not.toBe(unlockSecond)
    expect(unlockFirst).toContain('- div.passives[1] #1: <div class="relative opacity-50">')
    expect(unlockSecond).toContain('- div.passives[1] #2: <div class="relative opacity-50">')
  })

  it('opens one hunk per separate change', () => {
    const patch = diffHtml('<p>a</p><p>b</p><p>c</p>', '<p>A</p><p>b</p><p>C</p>')
    expect(patch.split('\n').filter((line) => line === '@@')).toHaveLength(2)
  })
})
