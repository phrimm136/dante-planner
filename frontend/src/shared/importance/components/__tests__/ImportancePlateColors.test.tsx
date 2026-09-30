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

function tintVariables(container: HTMLElement): string[] {
  return ['left', 'centre', 'right'].map((piece) => {
    const element = container.querySelector<HTMLElement>(`[data-slot="name-plate-${piece}"]`)
    return /^var\((--[\w-]+)\)$/.exec(element?.style.backgroundColor ?? '')?.[1] ?? ''
  })
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
      const colors = importanceColorCode.enemy[level]
      for (const tint of tintVariables(container)) {
        expect(banner?.style.getPropertyValue(tint)).toBe(colors.passivePanel)
      }
    },
  )
})
