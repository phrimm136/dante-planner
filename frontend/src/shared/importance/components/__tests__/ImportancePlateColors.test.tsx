import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import { StyledSkillName } from '@/shared/gameText'
import { ImportanceBanner } from '../ImportanceBanner'

function bodyAndTailVariables(container: HTMLElement): { body: string; tail: string } {
  const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"]')
  const stripes = plate?.firstElementChild as HTMLElement | null
  const lastStop = /var\((--[\w-]+)[^)]*\)\s+3\.4em\)\s*$/.exec(
    stripes?.style.backgroundImage ?? '',
  )
  const tailStop = /var\((--[\w-]+)[^)]*\)\s+1\.5em\)\s*$/.exec(plate?.style.backgroundImage ?? '')
  return { body: lastStop?.[1] ?? '', tail: tailStop?.[1] ?? '' }
}

describe('importance name plate colors', () => {
  it.each(['1', '2', '3'] as const)(
    'paints the level %s enemy plate body in the panel color and names it in the name color',
    (level) => {
      const { container } = render(
        <ImportanceBanner level={Number(level)} palette="enemy" className="row">
          <StyledSkillName name="Name" attributeType="NEUTRAL" />
        </ImportanceBanner>,
      )
      const banner = container.querySelector<HTMLElement>('[data-slot="importance-banner"]')
      const { body, tail } = bodyAndTailVariables(container)
      const colors = importanceColorCode.enemy[level]
      expect(banner?.style.getPropertyValue(body)).toBe(colors.passivePanel)
      expect(banner?.style.getPropertyValue(tail)).not.toBe(colors.passivePanel)
    },
  )
})
