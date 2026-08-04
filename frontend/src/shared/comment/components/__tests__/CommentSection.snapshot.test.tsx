/**
 * DOM snapshots of the comment section over every state that changes what it
 * renders: published against unpublished, authenticated against guest, empty
 * against populated tree, plus the pending, banner and moderator variants.
 *
 * Children are stubbed to prop echoes, so this pins the section's own
 * composition; the committed snapshots were produced by an innerHTML
 * comparison against the inline three-way composer branch, so they record its
 * markup exactly.
 */

import { describe, it, expect, vi } from 'vitest'

import { renderWithProviders } from '@/test-utils/renderWithProviders'
import { CommentSection } from '../CommentSection'
import { SECTION_CASES } from './commentSectionHarness'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

vi.mock('@/shared/auth', async () => (await import('./commentSectionHarness')).authModule())
vi.mock('../../hooks/useCommentsQuery', async () =>
  (await import('./commentSectionHarness')).commentsQueryModule(),
)
vi.mock('../../hooks/usePlannerCommentsSse', async () =>
  (await import('./commentSectionHarness')).sseModule(),
)
vi.mock('../../hooks/useCommentMutations', async () =>
  (await import('./commentSectionHarness')).mutationsModule(),
)
vi.mock('../../hooks/useModeratorCommentDelete', async () =>
  (await import('./commentSectionHarness')).moderatorDeleteModule(),
)
vi.mock('../CommentEditor', async () => (await import('./commentSectionHarness')).editorStub())
vi.mock('../CommentThread', async () => (await import('./commentSectionHarness')).threadStub())
vi.mock('../NewCommentsBar', async () =>
  (await import('./commentSectionHarness')).newCommentsBarStub(),
)
vi.mock('@/shared/moderation', async () =>
  (await import('./commentSectionHarness')).moderationStub(),
)

describe('CommentSection DOM', () => {
  for (const testCase of SECTION_CASES) {
    it(`renders ${testCase.label}`, () => {
      testCase.arrange()
      expect(
        renderWithProviders(<CommentSection {...testCase.props} />).container.innerHTML,
      ).toMatchSnapshot()
    })
  }
})
