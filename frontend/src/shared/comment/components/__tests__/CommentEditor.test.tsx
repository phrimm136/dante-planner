/**
 * The comment length gate. The server caps the submitted HTML, and the editor
 * submits `getHTML()`, so the counter has to measure the same string — a plain
 * text count lets a body through that the server then rejects.
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'

import { COMMENT_MAX_CHARS } from '@/lib/constants'
import { CommentEditor } from '../CommentEditor'

vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'EN' } }),
  }
})

const WRAPPER_LENGTH = '<p></p>'.length

/** A paragraph whose serialized HTML is exactly `htmlLength` characters. */
function paragraphOfHtmlLength(htmlLength: number) {
  return `<p>${'a'.repeat(htmlLength - WRAPPER_LENGTH)}</p>`
}

async function renderEditor(initialContent: string, expectedCount: number) {
  render(<CommentEditor onSubmit={() => {}} initialContent={initialContent} />)
  const counter = screen.getByText(new RegExp(`/${COMMENT_MAX_CHARS}$`))
  await waitFor(() => expect(counter).toHaveTextContent(`${expectedCount}/${COMMENT_MAX_CHARS}`))
  return counter
}

describe('CommentEditor length gate', () => {
  it('counts the submitted HTML, not its plain text', async () => {
    const counter = await renderEditor(paragraphOfHtmlLength(100), 100)

    expect(counter).toHaveTextContent(`100/${COMMENT_MAX_CHARS}`)
  })

  it('admits a body whose HTML is exactly at the limit', async () => {
    const counter = await renderEditor(paragraphOfHtmlLength(COMMENT_MAX_CHARS), COMMENT_MAX_CHARS)

    expect(counter.className).not.toContain('destructive')
  })

  it('refuses a body whose HTML is one character over the limit', async () => {
    const counter = await renderEditor(
      paragraphOfHtmlLength(COMMENT_MAX_CHARS + 1),
      COMMENT_MAX_CHARS + 1,
    )

    expect(counter.className).toContain('destructive')
  })

  it('refuses a body whose plain text is at the limit but whose HTML is over it', async () => {
    const counter = await renderEditor(
      `<p>${'a'.repeat(COMMENT_MAX_CHARS)}</p>`,
      COMMENT_MAX_CHARS + WRAPPER_LENGTH,
    )

    expect(counter.className).toContain('destructive')
  })
})
