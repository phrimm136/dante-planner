/**
 * The composer's three states — unpublished, published guest, published author
 * — as DOM snapshots, plus the submit passthrough. The editor is stubbed to a
 * button echoing its props.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { userEvent } from '@testing-library/user-event'

import { CommentComposer } from '../CommentComposer'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

vi.mock('../CommentEditor', () => ({
  CommentEditor: ({
    placeholder,
    isSubmitting,
    onSubmit,
  }: {
    placeholder?: string
    isSubmitting?: boolean
    onSubmit: (content: string) => void
  }) => (
    <button
      type="button"
      data-testid="comment-editor"
      data-placeholder={placeholder}
      data-submitting={String(isSubmitting)}
      onClick={() => onSubmit('body')}
    >
      editor
    </button>
  ),
}))

const BOOLS = [false, true]

const CASES = BOOLS.flatMap((isPublished) =>
  BOOLS.flatMap((isAuthenticated) =>
    BOOLS.map((isSubmitting) => ({
      label: [
        isPublished ? 'published' : 'unpublished',
        isAuthenticated ? 'auth' : 'guest',
        isSubmitting ? 'submitting' : 'idle',
      ].join('+'),
      props: { isPublished, isAuthenticated, isSubmitting, onSubmit: () => {} },
    })),
  ),
)

describe('CommentComposer DOM', () => {
  for (const testCase of CASES) {
    it(`renders ${testCase.label}`, () => {
      expect(render(<CommentComposer {...testCase.props} />).container.innerHTML).toMatchSnapshot()
    })
  }
})

describe('CommentComposer behaviour', () => {
  it('passes the editor submission through', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<CommentComposer isPublished isAuthenticated onSubmit={onSubmit} isSubmitting={false} />)

    await user.click(screen.getByTestId('comment-editor'))
    expect(onSubmit).toHaveBeenCalledWith('body')
  })

  it('offers no editor to a guest or on an unpublished planner', () => {
    const { rerender } = render(
      <CommentComposer
        isPublished
        isAuthenticated={false}
        onSubmit={() => {}}
        isSubmitting={false}
      />,
    )
    expect(screen.queryByTestId('comment-editor')).toBeNull()

    rerender(
      <CommentComposer
        isPublished={false}
        isAuthenticated
        onSubmit={() => {}}
        isSubmitting={false}
      />,
    )
    expect(screen.queryByTestId('comment-editor')).toBeNull()
  })
})
