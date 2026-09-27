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
import { EXPORT_FILE_EXTENSION, EXPORT_VERSION } from '@/lib/constants'
import { GZIP_OS_BYTE_OFFSET, GZIP_OS_TOPS20 } from '../../lib/deckCode'

import type { SaveablePlanner } from '../../types/PlannerTypes'

const PLANNER_ID = '00000000-0000-4000-8000-000000000001'

const storageMocks = vi.hoisted(() => ({
  listLocal: vi.fn(async (): Promise<unknown[]> => []),
  loadFromLocal: vi.fn(async (_id: string): Promise<unknown> => ({ ok: false, error: 'missing' })),
  saveToLocal: vi.fn(async (_planner: unknown): Promise<unknown> => ({ ok: true })),
}))

vi.mock('../../hooks/usePlannerStorage', () => ({
  usePlannerStorage: () => storageMocks,
}))

const validationMocks = vi.hoisted(() => ({
  validatePlannerForImport: vi.fn(
    (_planner: unknown): { key: string; params?: Record<string, string> } | null => null,
  ),
}))

vi.mock('../../lib/plannerValidation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../lib/plannerValidation')>()),
  validatePlannerForImport: validationMocks.validatePlannerForImport,
}))

vi.mock('../../hooks/usePlannerIdRegistry', () => ({
  usePlannerIdRegistry: () => () => undefined,
}))

vi.mock('@/pages/egoGift', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/pages/egoGift')>()),
  useEGOGiftListSpec: () => ({}),
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
import { showSuccess, showWarning } from '@/lib/errorPresentation'

/** The planner both sides hold, which makes the import a conflict. */
const EXISTING: SaveablePlanner = buildSaveablePlanner({
  metadata: { id: PLANNER_ID, title: 'Existing Run' },
})

/** A .danteplanner file carrying one planner the local store already has. */
function conflictingImportFile(): File {
  return importFile([
    {
      id: PLANNER_ID,
      metadata: { ...EXISTING.metadata, title: 'Imported Run' },
      config: EXISTING.config,
      content: EXISTING.content,
    },
  ])
}

function importFile(planners: unknown[]): File {
  const envelope = {
    exportVersion: EXPORT_VERSION,
    exportedAt: '2026-01-01T00:00:00.000Z',
    planners,
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

describe('PlannerExportImportSection invalid planners', () => {
  beforeEach(() => {
    storageMocks.listLocal.mockResolvedValue([])
    storageMocks.saveToLocal.mockReset()
    storageMocks.saveToLocal.mockResolvedValue({ ok: true })
    validationMocks.validatePlannerForImport.mockImplementation((planner) =>
      (planner as SaveablePlanner).metadata.title === 'Plan 3'
        ? { key: 'pages.plannerMD.validation.unknownIdentityId', params: { id: '10199' } }
        : null,
    )
  })

  it('imports the valid planners and warns about the one it skipped', async () => {
    const user = userEvent.setup()
    const items = [1, 2, 3].map((n) => {
      const id = `00000000-0000-4000-8000-00000000001${n}`
      return {
        id,
        metadata: { ...EXISTING.metadata, id, title: `Plan ${n}` },
        config: EXISTING.config,
        content: EXISTING.content,
      }
    })
    const { container } = render(<PlannerExportImportSection />)

    const input = container.querySelector('input[type="file"]')!
    await user.upload(input as HTMLInputElement, importFile(items))

    await waitFor(() => expect(showWarning).toHaveBeenCalled())
    expect(
      storageMocks.saveToLocal.mock.calls.map(([p]) => (p as SaveablePlanner).metadata.title),
    ).toEqual(['Plan 1', 'Plan 2'])
    expect(showSuccess).toHaveBeenCalledWith('common:exportImport.importSuccess', { count: 2 })
    expect(showWarning).toHaveBeenCalledWith('common:exportImport.skippedInvalid', {
      count: 1,
      titles: 'Plan 3',
    })
  })

  it('reports no success when every planner in the file is rejected', async () => {
    validationMocks.validatePlannerForImport.mockReturnValue({
      key: 'pages.plannerMD.validation.unknownIdentityId',
      params: { id: '10199' },
    })
    vi.mocked(showSuccess).mockClear()
    const user = userEvent.setup()
    const items = [4, 5].map((n) => {
      const id = `00000000-0000-4000-8000-00000000002${n}`
      return {
        id,
        metadata: { ...EXISTING.metadata, id, title: `Plan ${n}` },
        config: EXISTING.config,
        content: EXISTING.content,
      }
    })
    const { container } = render(<PlannerExportImportSection />)

    const input = container.querySelector('input[type="file"]')!
    await user.upload(input as HTMLInputElement, importFile(items))

    await waitFor(() =>
      expect(showWarning).toHaveBeenCalledWith('common:exportImport.skippedInvalid', {
        count: 2,
        titles: 'Plan 4, Plan 5',
      }),
    )
    expect(storageMocks.saveToLocal).not.toHaveBeenCalled()
    expect(showSuccess).not.toHaveBeenCalledWith(
      'common:exportImport.importSuccess',
      expect.anything(),
    )
  })
})
