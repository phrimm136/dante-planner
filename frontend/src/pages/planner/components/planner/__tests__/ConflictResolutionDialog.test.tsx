import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ConflictResolutionDialog } from '../ConflictResolutionDialog'

// Mock react-i18next with initReactI18next for proper module loading
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, defaultValue?: string | Record<string, unknown>) => {
        if (typeof defaultValue === 'string') return defaultValue
        if (typeof defaultValue === 'object' && 'defaultValue' in defaultValue) {
          return defaultValue.defaultValue as string
        }
        return key
      },
    }),
  }
})

describe('ConflictResolutionDialog', () => {
  const defaultProps = {
    open: true,
    conflictState: {
      serverVersion: 3,
      detectedAt: '2026-01-04T12:00:00Z',
    },
    onChoice: vi.fn(),
  }

  it('should render when open is true', () => {
    render(<ConflictResolutionDialog {...defaultProps} />)

    expect(screen.getByText('Save Conflict Detected')).toBeInTheDocument()
    expect(screen.getByText(/This planner was modified on another device/)).toBeInTheDocument()
  })

  it('should not render when open is false', () => {
    render(<ConflictResolutionDialog {...defaultProps} open={false} />)

    expect(screen.queryByText('Save Conflict Detected')).not.toBeInTheDocument()
  })

  it('should call onChoice with overwrite when Keep Local button is clicked', () => {
    const onChoice = vi.fn()
    render(<ConflictResolutionDialog {...defaultProps} onChoice={onChoice} />)

    fireEvent.click(screen.getByText('Keep Local'))

    expect(onChoice).toHaveBeenCalledWith('overwrite')
  })

  it('should call onChoice with discard when Use Server button is clicked', () => {
    const onChoice = vi.fn()
    render(<ConflictResolutionDialog {...defaultProps} onChoice={onChoice} />)

    fireEvent.click(screen.getByText('Use Server'))

    expect(onChoice).toHaveBeenCalledWith('discard')
  })

  it('should disable buttons when isResolving is true', () => {
    render(<ConflictResolutionDialog {...defaultProps} isResolving={true} />)

    expect(screen.getByText('Keep Local')).toBeDisabled()
    expect(screen.getByText('Use Server')).toBeDisabled()
  })
})

describe('ConflictResolutionDialog - failed resolution', () => {
  const baseProps = {
    open: true,
    conflictState: { serverVersion: 3, detectedAt: '2026-01-04T12:00:00Z' },
    onChoice: vi.fn(),
  }

  it('reports a failure the presenter has a message for', () => {
    render(<ConflictResolutionDialog {...baseProps} resolutionError={{ kind: 'quota' }} />)

    expect(screen.getByText('planner:pages.plannerMD.save.quotaExceeded')).toBeInTheDocument()
  })

  it('speaks for a failure that is itself a conflict, which the presenter will not', () => {
    // presentError yields null for a conflict precisely because this dialog owns it.
    render(
      <ConflictResolutionDialog
        {...baseProps}
        resolutionError={{ kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 9 }}
      />,
    )

    expect(screen.getByText(/changed again/)).toBeInTheDocument()
  })

  it('says nothing about a failure when none has happened', () => {
    render(<ConflictResolutionDialog {...baseProps} />)

    expect(screen.queryByText(/changed again/)).toBeNull()
  })

  it('warns that the copy stays unpublished, since keep-both is always on offer', () => {
    render(<ConflictResolutionDialog {...baseProps} />)

    expect(screen.getByText('The copy will not be published')).toBeInTheDocument()
  })
})
