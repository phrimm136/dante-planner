import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SkillReplacementSection } from '../SkillReplacementSection'
import { createDefaultEquipment } from '../../../stores/usePlannerStore'
import { DEFAULT_SKILL_EA } from '@/shared/gameData'
import type { SkillEAState } from '../../../types/DeckTypes'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return { ...actual, useTranslation: () => ({ t: (key: string) => key }) }
})

vi.mock('@/pages/identity', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/pages/identity')>()),
  useIdentityListSpec: () => ({}),
}))

vi.mock('../../../hooks/useSkillReplacementLayout', () => ({
  useSkillReplacementLayout: () => ({ gridStyle: {}, mobileScale: 1 }),
}))

vi.mock('../SinnerSkillCard', () => ({
  SinnerSkillCard: ({ identityId, onClick }: { identityId: string; onClick: () => void }) => (
    <button type="button" onClick={onClick}>
      {`open ${identityId}`}
    </button>
  ),
}))

vi.mock('../SkillExchangeModal', () => ({
  SkillExchangeModal: ({
    onExchange,
    onReset,
  }: {
    onExchange: (source: 0 | 1 | 2, target: 0 | 1 | 2) => void
    onReset: () => void
  }) => (
    <div>
      <button type="button" onClick={() => onExchange(0, 1)}>
        exchange 0 to 1
      </button>
      <button type="button" onClick={onReset}>
        reset sinner
      </button>
    </div>
  ),
}))

const EQUIPMENT = createDefaultEquipment(60)
const PLANNED: Record<string, SkillEAState> = {
  '1': { ...DEFAULT_SKILL_EA },
  '2': { ...DEFAULT_SKILL_EA },
}
const CURRENT: Record<string, SkillEAState> = {
  '1': { 0: 1, 1: 2, 2: 1 },
  '2': { 0: 0, 1: 2, 2: 1 },
}
const SINNER_ONE_IDENTITY = EQUIPMENT['1']?.identity.id ?? ''

async function openSinnerOne() {
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: `open ${SINNER_ONE_IDENTITY}` }))
  return user
}

describe('SkillReplacementSection', () => {
  it("an exchange writes only that sinner's current counts", async () => {
    const onSinnerSkillEAChange = vi.fn()
    render(
      <SkillReplacementSection
        equipment={EQUIPMENT}
        plannedEAState={PLANNED}
        currentEAState={CURRENT}
        onSinnerSkillEAChange={onSinnerSkillEAChange}
      />,
    )
    const user = await openSinnerOne()

    await user.click(screen.getByRole('button', { name: 'exchange 0 to 1' }))

    expect(onSinnerSkillEAChange.mock.calls).toEqual([['1', { 0: 0, 1: 3, 2: 1 }]])
  })

  it('a reset writes only that sinner, back to the default counts', async () => {
    const onSinnerSkillEAChange = vi.fn()
    render(
      <SkillReplacementSection
        equipment={EQUIPMENT}
        plannedEAState={PLANNED}
        currentEAState={CURRENT}
        onSinnerSkillEAChange={onSinnerSkillEAChange}
      />,
    )
    const user = await openSinnerOne()

    await user.click(screen.getByRole('button', { name: 'reset sinner' }))

    expect(onSinnerSkillEAChange.mock.calls).toEqual([['1', DEFAULT_SKILL_EA]])
  })

  it('without a callback the handlers write nothing', async () => {
    render(
      <SkillReplacementSection
        equipment={EQUIPMENT}
        plannedEAState={PLANNED}
        currentEAState={CURRENT}
      />,
    )
    const user = await openSinnerOne()

    await user.click(screen.getByRole('button', { name: 'exchange 0 to 1' }))
    await user.click(screen.getByRole('button', { name: 'reset sinner' }))

    expect(screen.getByRole('button', { name: 'exchange 0 to 1' })).toBeInTheDocument()
  })
})
