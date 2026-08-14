/**
 * PlannerExportImportSection.test.tsx
 *
 * Closing the conflict dialog cancels the import step. Left busy behind a closed
 * dialog, the section would refuse every later export and import.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { gzip } from 'pako'

import { buildSaveablePlanner } from '@/test-utils/fixtures'
import { EXPORT_VERSION, EXPORT_FILE_EXTENSION } from '@/lib/constants'
import { GZIP_OS_BYTE_OFFSET, GZIP_OS_TOPS20 } from '../../lib/deckCode'

import type { SaveablePlanner } from '../../types/PlannerTypes'

const PLANNER_ID = '00000000-0000-4000-8000-000000000001'

const storageMocks = vi.hoisted(() => ({
  listLocal: vi.fn(async (): Promise<unknown[]> => []),
  loadFromLocal: vi.fn(async (_id: string): Promise<unknown> => ({ ok: false, error: 'missing' })),
  saveToLocal: vi.fn(async (_planner: unknown): Promise<unknown> => ({ ok: true })),
  getOrCreateDeviceId: vi.fn(async (): Promise<unknown> => ({ ok: true, value: 'device-1' })),
}))

vi.mock('../../hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => storageMocks,
}))

vi.mock('@/lib/errorPresentation', () => ({
  showError: vi.fn(),
  showErrorMessage: vi.fn(),
  showInfo: vi.fn(),
  showSuccess: vi.fn(),
  showWarning: vi.fn(),
}))

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, fallback?: unknown) => (typeof fallback === 'string' ? fallback : key),
    }),
  }
})

import { PlannerExportImportSection } from '../PlannerExportImportSection'

/** The planner both sides hold, which makes the import a conflict. */
const EXISTING: SaveablePlanner = buildSaveablePlanner({
  metadata: { id: PLANNER_ID, title: 'Existing Run' },
})

/** A .danteplanner file carrying one planner the local store already has. */
function conflictingImportFile(): File {
  const envelope = {
    exportVersion: EXPORT_VERSION,
    exportedAt: '2026-01-01T00:00:00.000Z',
    sourceDeviceId: 'other-device',
    planners: [
      {
        id: PLANNER_ID,
        metadata: { ...EXISTING.metadata, title: 'Imported Run', deviceId: '' },
        config: EXISTING.config,
        content: EXISTING.content,
      },
    ],
  }

  const compressed = gzip(JSON.stringify(envelope))
  // The reader checks the OS byte the exporter stamps.
  compressed[GZIP_OS_BYTE_OFFSET] = GZIP_OS_TOPS20
  return new File([compressed], `plans${EXPORT_FILE_EXTENSION}`, { type: 'application/gzip' })
}

describe('PlannerExportImportSection conflict dismissal', () => {
  beforeEach(() => {
    storageMocks.listLocal.mockResolvedValue([{ id: PLANNER_ID, title: 'Existing Run' }])
    storageMocks.loadFromLocal.mockResolvedValue({ ok: true, value: EXISTING })
    storageMocks.saveToLocal.mockResolvedValue({ ok: true })
    storageMocks.getOrCreateDeviceId.mockResolvedValue({ ok: true, value: 'device-1' })
  })

  it('returns to idle when the user closes the conflict dialog', async () => {
    const user = userEvent.setup()
    const { container } = render(<PlannerExportImportSection />)

    const input = container.querySelector('input[type="file"]')!
    await user.upload(input as HTMLInputElement, conflictingImportFile())

    // The import stops at the conflict and holds the dialog open.
    await waitFor(() => expect(screen.getByText('Existing Run')).toBeInTheDocument())
    // The open dialog hides the rest of the page from the accessibility tree.
    expect(screen.getByRole('button', { name: 'Export', hidden: true })).toBeDisabled()

    await user.keyboard('{Escape}')

    // Cancelled: the conflicted planner is skipped and the section is usable again.
    await waitFor(() => expect(screen.queryByText('Existing Run')).toBeNull())
    expect(screen.getByRole('button', { name: 'Export' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Import' })).toBeEnabled()
    expect(storageMocks.saveToLocal).not.toHaveBeenCalled()
  })
})
