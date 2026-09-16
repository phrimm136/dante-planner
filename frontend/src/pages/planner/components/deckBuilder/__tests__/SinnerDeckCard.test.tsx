import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { SinnerDeckCard } from '../SinnerDeckCard'
import { FORMATION_SLOT_DIM } from '@/pages/identity'
import { MAX_DEPLOYED_ORDER } from '@/lib/constants'
import { asIdentityId } from '@/test-utils/fixtures'
import type { SinnerEquipment } from '../../../types/DeckTypes'

vi.mock('@/pages/identity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/pages/identity')>()

  return {
    ...actual,
    IdentityCard: ({ dim, overlay }: { dim?: number; overlay?: React.ReactNode }) => (
      <div data-testid="identity-card" data-dim={dim === undefined ? 'none' : String(dim)}>
        {overlay}
      </div>
    ),
    FormationBadge: ({ state, order }: { state: string; order: number }) => (
      <span data-testid="formation-badge" data-state={state}>
        {order}
      </span>
    ),
  }
})

const equipment: SinnerEquipment = {
  identity: { id: asIdentityId('10101'), uptie: 4, level: 55 },
  egos: {},
}

function renderCard(deploymentOrder: number | null) {
  return render(
    <SinnerDeckCard
      sinnerName="Yisang"
      sinnerIndex={0}
      equipment={equipment}
      identityData={undefined}
      skillData={{ affinities: [], atkTypes: [] }}
      egoAffinityMap={{}}
      deploymentOrder={deploymentOrder}
      mobileScale={1}
      readOnly
    />,
  )
}

describe('SinnerDeckCard formation state', () => {
  it('draws an order inside the deployed window as a deployed slot', () => {
    renderCard(MAX_DEPLOYED_ORDER)

    expect(screen.getByTestId('formation-badge')).toHaveAttribute('data-state', 'deployed')
  })

  it('draws the first order past the window as a backup slot', () => {
    renderCard(MAX_DEPLOYED_ORDER + 1)

    expect(screen.getByTestId('formation-badge')).toHaveAttribute('data-state', 'backup')
  })

  it.each([MAX_DEPLOYED_ORDER, MAX_DEPLOYED_ORDER + 1])(
    'multiplies the body of the slot at order %i by `_grayColor`',
    (order) => {
      renderCard(order)

      expect(screen.getByTestId('identity-card')).toHaveAttribute(
        'data-dim',
        String(FORMATION_SLOT_DIM),
      )
    },
  )

  it('leaves an unassigned slot without a banner and without a multiplier', () => {
    renderCard(null)

    expect(screen.queryByTestId('formation-badge')).toBeNull()
    expect(screen.getByTestId('identity-card')).toHaveAttribute('data-dim', 'none')
  })
})
