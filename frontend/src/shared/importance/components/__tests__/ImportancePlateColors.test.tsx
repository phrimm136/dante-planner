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

function tintDiagonal(container: HTMLElement): number[] {
  const values = container
    .querySelector('svg[data-slot="name-plate-tint"] feColorMatrix')
    ?.getAttribute('values')
  const matrix = (values ?? '').trim().split(/\s+/).map(Number)
  return [0, 6, 12, 18].map((index) => matrix[index] ?? Number.NaN)
}

function channelDiagonal(hex: string): number[] {
  return [
    ...[1, 3, 5].map((start) =>
      Number((parseInt(hex.slice(start, start + 2), 16) / 255).toFixed(4)),
    ),
    1,
  ]
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
      const colors = importanceColorCode.enemy[level]
      expect(tintDiagonal(container)).toEqual(channelDiagonal(colors.passivePanel))
    },
  )
})
