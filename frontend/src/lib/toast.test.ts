/**
 * toast.test.ts
 *
 * Tests for the toast wrapper that injects a contact-support description
 * into every error toast via Proxy interception.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast as sonnerToast } from 'sonner'

const CONTACT_MESSAGE = 'If this error persists, please contact contact@dante-planner.com.'

vi.mock('@/lib/i18n', () => ({
  default: {
    t: (key: string) =>
      key === 'errors.contactOnRepeat'
        ? 'If this error persists, please contact contact@dante-planner.com.'
        : key,
  },
}))

vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

import { toast } from './toast'

describe('toast wrapper', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('toast.error', () => {
    it('injects contact description by default', () => {
      toast.error('Something went wrong')

      expect(sonnerToast.error).toHaveBeenCalledWith('Something went wrong', {
        description: CONTACT_MESSAGE,
      })
    })

    it('preserves explicit description when provided', () => {
      toast.error('Upload failed', { description: 'Check file size' })

      expect(sonnerToast.error).toHaveBeenCalledWith('Upload failed', {
        description: 'Check file size',
      })
    })

    it('preserves other options alongside injected description', () => {
      toast.error('Save failed', { duration: 5000 })

      expect(sonnerToast.error).toHaveBeenCalledWith('Save failed', {
        duration: 5000,
        description: CONTACT_MESSAGE,
      })
    })

    it('works with no options argument', () => {
      toast.error('Network error')

      expect(sonnerToast.error).toHaveBeenCalledOnce()
      const call = vi.mocked(sonnerToast.error).mock.calls[0]
      expect(call[1]).toEqual({ description: CONTACT_MESSAGE })
    })
  })

  describe('pass-through methods', () => {
    it('toast.success passes through without description injection', () => {
      toast.success('Saved')

      expect(sonnerToast.success).toHaveBeenCalledWith('Saved')
      expect(sonnerToast.error).not.toHaveBeenCalled()
    })

    it('toast.warning passes through without description injection', () => {
      toast.warning('Low storage')

      expect(sonnerToast.warning).toHaveBeenCalledWith('Low storage')
      expect(sonnerToast.error).not.toHaveBeenCalled()
    })

    it('toast.dismiss passes through', () => {
      toast.dismiss('toast-id')

      expect(sonnerToast.dismiss).toHaveBeenCalledWith('toast-id')
    })
  })
})
