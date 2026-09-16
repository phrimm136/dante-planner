import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'

import { FormationBadge } from '../FormationBadge'
import { formationSlotLayers } from '../../lib/cardLayout'

vi.mock('@/shared/assets', () => ({
  getFormationBadgePath: (state: string) => `/mock/deploy-${state}.png`,
}))

describe('FormationBadge', () => {
  it('draws the deployed banner over its order number', () => {
    render(<FormationBadge state="deployed" order={3} />)

    expect(screen.getByRole('presentation')).toHaveAttribute('src', '/mock/deploy-selected.png')
    expect(screen.getByTestId('formation-order')).toHaveTextContent('3')
  })

  it('draws the backup banner over its own order number', () => {
    render(<FormationBadge state="backup" order={9} />)

    expect(screen.getByRole('presentation')).toHaveAttribute('src', '/mock/deploy-backup.png')
    expect(screen.getByTestId('formation-order')).toHaveTextContent('9')
  })

  it('inks each state`s order number in its own material colour', () => {
    const deployed = render(<FormationBadge state="deployed" order={1} />)
    expect(deployed.getByTestId('formation-order')).toHaveStyle({ color: '#FFCB00' })
    deployed.unmount()

    render(<FormationBadge state="backup" order={9} />)
    expect(screen.getByTestId('formation-order')).toHaveStyle({ color: '#22FFE4' })
  })

  it('places the banner at the box its state`s offsets give it', () => {
    render(<FormationBadge state="backup" order={9} />)
    const rect = formationSlotLayers('backup').banner.rect

    expect(screen.getByRole('presentation')).toHaveStyle({
      left: `${String(rect.left)}%`,
      width: `${String(rect.width)}%`,
      objectFit: 'contain',
    })
  })

  it('takes no pointer events, so the card underneath stays clickable', () => {
    const { container } = render(<FormationBadge state="deployed" order={1} />)

    expect(container.firstElementChild).toHaveClass('pointer-events-none')
  })
})
