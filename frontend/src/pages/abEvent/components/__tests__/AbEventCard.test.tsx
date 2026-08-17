import { describe, it, expect, vi } from 'vitest'
import { render } from '@testing-library/react'
import { AbEventCard } from '../AbEventCard'

vi.mock('@/shared/assets', () => ({
  getAbEventImagePath: (id: string) => `/images/abEvent/${id}.webp`,
}))

function getImage(container: HTMLElement) {
  return container.querySelector('img')
}

describe('AbEventCard', () => {
  describe('Image rendering', () => {
    it('renders the event art from illustId when provided', () => {
      const { container } = render(<AbEventCard eventId="901001" hasImage illustId="971096" />)

      expect(getImage(container)).toHaveAttribute('src', '/images/abEvent/971096.webp')
    })

    it('falls back to eventId for the image path when illustId is absent', () => {
      const { container } = render(<AbEventCard eventId="901001" hasImage />)

      expect(getImage(container)).toHaveAttribute('src', '/images/abEvent/901001.webp')
    })

    it('renders the eventId placeholder instead of an image when there is no art', () => {
      const { container } = render(<AbEventCard eventId="901001" hasImage={false} />)

      expect(getImage(container)).toBeNull()
      expect(container.textContent).toContain('901001')
    })
  })

  describe('Lazy loading', () => {
    it('lazy-loads the event art so off-screen grid cards defer their fetch', () => {
      const { container } = render(<AbEventCard eventId="901001" hasImage />)

      expect(getImage(container)).toHaveAttribute('loading', 'lazy')
    })
  })
})
