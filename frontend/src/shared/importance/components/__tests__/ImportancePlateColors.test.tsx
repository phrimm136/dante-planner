import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'

import importanceColorCode from '@static/data/color/importanceColorCode.json'
import krTable from '@static/data/fontAdvances/KR.json'
import { FontAdvanceTableSchema } from '@/shared/cardLayout'
import { NamePlate } from '@/shared/gameText'

const KR_TABLE = FontAdvanceTableSchema.parse(krTable)

vi.mock('@/shared/cardLayout', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/cardLayout')>()),
  useFontAdvances: () => KR_TABLE,
}))

function bodyAndTailVariables(container: HTMLElement): { body: string; tail: string } {
  const plate = container.querySelector<HTMLElement>('[data-slot="name-plate"]')
  const stripes = plate?.firstElementChild as HTMLElement | null
  const lastStop = /var\((--[\w-]+)[^)]*\)\s+3\.4rem\)\s*$/.exec(
    stripes?.style.backgroundImage ?? '',
  )
  const tailStop = /var\((--[\w-]+)[^)]*\)\s+1\.5rem\)\s*$/.exec(plate?.style.backgroundImage ?? '')
  return { body: lastStop?.[1] ?? '', tail: tailStop?.[1] ?? '' }
}

describe('importance name plate colors', () => {
  it.each(['1', '2', '3'] as const)(
    'paints the level %s enemy plate body in the panel color and names it in the name color',
    (level) => {
      const { container } = render(
        <NamePlate
          kind="passive"
          name="Name"
          attributeType="NEUTRAL"
          importance={{ level: Number(level), palette: 'enemy' }}
        />,
      )
      const banner = container.querySelector<HTMLElement>('[data-slot="importance-banner"]')
      const { body, tail } = bodyAndTailVariables(container)
      const colors = importanceColorCode.enemy[level]
      expect(banner?.style.getPropertyValue(body)).toBe(colors.passivePanel)
      expect(banner?.style.getPropertyValue(tail)).not.toBe(colors.passivePanel)
    },
  )
})
