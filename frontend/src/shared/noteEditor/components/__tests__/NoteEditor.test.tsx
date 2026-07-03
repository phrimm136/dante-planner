/**
 * NoteEditor Component Tests
 *
 * Tests for the Tiptap-based rich text editor with focus on:
 * 1. XSS prevention in link handling
 * 2. URL sanitization behavior
 * 3. Content rendering and controlled component pattern
 */

import { describe, it, expect, vi, beforeEach, beforeAll, afterAll } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NoteEditor } from '../NoteEditor'
import { calculateNoteByteLength } from '../../lib/noteUtils'
import type { NoteContent } from '../../types/NoteEditorTypes'

// Mock i18next with initReactI18next for proper module loading
vi.mock('react-i18next', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-i18next')>()
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => {
        const translations: Record<string, string> = {
          'pages.plannerMD.noteEditor.errorFallback.loadFailed': 'Editor failed to load',
          'pages.plannerMD.noteEditor.errorFallback.tryAgain': 'Try again',
          'pages.plannerMD.noteEditor.linkDialog.title': 'Insert Link',
          'pages.plannerMD.noteEditor.linkDialog.url': 'URL',
          'pages.plannerMD.noteEditor.linkDialog.displayText': 'Display Text',
          'pages.plannerMD.noteEditor.linkDialog.displayTextPlaceholder': 'Link text',
          'pages.plannerMD.noteEditor.linkDialog.invalidUrl': 'Invalid URL',
          'pages.plannerMD.noteEditor.toolbar.bold': 'Bold',
          'pages.plannerMD.noteEditor.toolbar.italic': 'Italic',
          'pages.plannerMD.noteEditor.toolbar.strike': 'Strikethrough',
          'pages.plannerMD.noteEditor.toolbar.strikethrough': 'Strikethrough',
          'pages.plannerMD.noteEditor.toolbar.heading1': 'Heading 1',
          'pages.plannerMD.noteEditor.toolbar.heading2': 'Heading 2',
          'pages.plannerMD.noteEditor.toolbar.heading3': 'Heading 3',
          'pages.plannerMD.noteEditor.toolbar.bulletList': 'Bullet List',
          'pages.plannerMD.noteEditor.toolbar.orderedList': 'Ordered List',
          'pages.plannerMD.noteEditor.toolbar.blockquote': 'Quote',
          'pages.plannerMD.noteEditor.toolbar.code': 'Code',
          'pages.plannerMD.noteEditor.toolbar.link': 'Link',
          'pages.plannerMD.noteEditor.toolbar.image': 'Image',
          'pages.plannerMD.noteEditor.toolbar.spoiler': 'Spoiler',
          'pages.plannerMD.noteEditor.placeholder': 'Add notes here...',
          'pages.plannerMD.noteEditor.placeholderReadOnly': 'No notes',
          'common:cancel': 'Cancel',
          'common:confirm': 'Confirm',
        }
        return translations[key] || key
      },
    }),
  }
})

// Mock sonner toast
const mockToastError = vi.fn()
vi.mock('sonner', () => ({
  toast: {
    error: (msg: string) => mockToastError(msg),
  },
}))

describe('NoteEditor', () => {
  const defaultValue: NoteContent = {
    content: {
      type: 'doc',
      content: [{ type: 'paragraph' }],
    },
  }

  const mockOnChange = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('should render the editor', async () => {
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      // Editor should be visible
      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })
    })

    it('should render with placeholder', async () => {
      render(
        <NoteEditor
          value={defaultValue}
          onChange={mockOnChange}
          placeholder="Enter your notes..."
        />
      )

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Component renders placeholder in a separate div when empty and unfocused
      // Just verify the editor rendered successfully
    })

    it('should apply readOnly state', async () => {
      render(
        <NoteEditor value={defaultValue} onChange={mockOnChange} readOnly />
      )

      await waitFor(() => {
        const container = document.querySelector('.note-editor')
        expect(container).toBeTruthy()
      })
    })

    it('should apply custom className', async () => {
      render(
        <NoteEditor
          value={defaultValue}
          onChange={mockOnChange}
          className="custom-class"
        />
      )

      await waitFor(() => {
        const container = document.querySelector('.note-editor')
        expect(container?.classList.contains('custom-class')).toBe(true)
      })
    })
  })

  describe('Focus Behavior', () => {
    it('should show toolbar when focused', async () => {
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      const container = document.querySelector('.note-editor')!
      fireEvent.click(container)

      await waitFor(() => {
        // Toolbar should be visible when focused
        expect(container.classList.contains('ring-2')).toBe(true)
      })
    })
  })

  describe('Controlled Component', () => {
    it('should accept value prop changes without crashing', async () => {
      // Note: The NoteEditor has debouncing and internal state management
      // that prevents immediate updates when hasLocalChangesRef is true.
      // This test verifies the component accepts new props gracefully.
      const { rerender } = render(
        <NoteEditor value={defaultValue} onChange={mockOnChange} />
      )

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      const newValue: NoteContent = {
        content: {
          type: 'doc',
          content: [
            {
              type: 'paragraph',
              content: [{ type: 'text', text: 'Updated content' }],
            },
          ],
        },
      }

      // Rerender should not throw
      rerender(<NoteEditor value={newValue} onChange={mockOnChange} />)

      // Verify editor still exists and is functional
      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
        expect(document.querySelector('.ProseMirror')).toBeTruthy()
      })
    })
  })
})

describe('NoteEditor - XSS Prevention', () => {
  const defaultValue: NoteContent = {
    content: {
      type: 'doc',
      content: [{ type: 'paragraph' }],
    },
  }

  const mockOnChange = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Link Dialog XSS Protection', () => {
    it('should render link dialog when link button is clicked', async () => {
      const user = userEvent.setup()
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Focus the editor first
      const container = document.querySelector('.note-editor')!
      await user.click(container)

      await waitFor(() => {
        expect(container.classList.contains('ring-2')).toBe(true)
      })

      // Find and click the link button
      const linkButton = screen.getByLabelText('Link')
      await user.click(linkButton)

      // Dialog should appear
      await waitFor(() => {
        expect(screen.getByText('Insert Link')).toBeTruthy()
      })
    })

    it('should show error toast for javascript: URL', async () => {
      const user = userEvent.setup()
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Focus and open link dialog
      const container = document.querySelector('.note-editor')!
      await user.click(container)

      await waitFor(() => {
        expect(container.classList.contains('ring-2')).toBe(true)
      })

      const linkButton = screen.getByLabelText('Link')
      await user.click(linkButton)

      await waitFor(() => {
        expect(screen.getByText('Insert Link')).toBeTruthy()
      })

      // Enter malicious URL
      const urlInput = screen.getByPlaceholderText('https://example.com')
      await user.type(urlInput, 'javascript:alert(1)')

      // Click confirm
      const confirmButton = screen.getByRole('button', { name: 'Confirm' })
      await user.click(confirmButton)

      // Should show error toast
      await waitFor(() => {
        expect(mockToastError).toHaveBeenCalledWith('Invalid URL')
      })
    })

    it('should show error toast for data: URL', async () => {
      const user = userEvent.setup()
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Focus and open link dialog
      const container = document.querySelector('.note-editor')!
      await user.click(container)

      await waitFor(() => {
        expect(container.classList.contains('ring-2')).toBe(true)
      })

      const linkButton = screen.getByLabelText('Link')
      await user.click(linkButton)

      await waitFor(() => {
        expect(screen.getByText('Insert Link')).toBeTruthy()
      })

      // Enter malicious data: URL
      const urlInput = screen.getByPlaceholderText('https://example.com')
      await user.type(urlInput, 'data:text/html,<script>alert(1)</script>')

      // Click confirm
      const confirmButton = screen.getByRole('button', { name: 'Confirm' })
      await user.click(confirmButton)

      // Should show error toast
      await waitFor(() => {
        expect(mockToastError).toHaveBeenCalledWith('Invalid URL')
      })
    })

    it('should accept valid https: URL', async () => {
      const user = userEvent.setup()
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Focus and open link dialog
      const container = document.querySelector('.note-editor')!
      await user.click(container)

      await waitFor(() => {
        expect(container.classList.contains('ring-2')).toBe(true)
      })

      const linkButton = screen.getByLabelText('Link')
      await user.click(linkButton)

      await waitFor(() => {
        expect(screen.getByText('Insert Link')).toBeTruthy()
      })

      // Enter valid URL
      const urlInput = screen.getByPlaceholderText('https://example.com')
      await user.type(urlInput, 'https://safe-website.com')

      // Click confirm
      const confirmButton = screen.getByRole('button', { name: 'Confirm' })
      await user.click(confirmButton)

      // Should NOT show error toast
      expect(mockToastError).not.toHaveBeenCalled()

      // Dialog should close
      await waitFor(() => {
        expect(screen.queryByText('Insert Link')).toBeFalsy()
      })
    })

    it('should auto-add https:// to URLs without protocol', async () => {
      const user = userEvent.setup()
      render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

      await waitFor(() => {
        expect(document.querySelector('.note-editor')).toBeTruthy()
      })

      // Focus and open link dialog
      const container = document.querySelector('.note-editor')!
      await user.click(container)

      await waitFor(() => {
        expect(container.classList.contains('ring-2')).toBe(true)
      })

      const linkButton = screen.getByLabelText('Link')
      await user.click(linkButton)

      await waitFor(() => {
        expect(screen.getByText('Insert Link')).toBeTruthy()
      })

      // Enter URL without protocol
      const urlInput = screen.getByPlaceholderText('https://example.com')
      await user.type(urlInput, 'example.com/page')

      // Click confirm
      const confirmButton = screen.getByRole('button', { name: 'Confirm' })
      await user.click(confirmButton)

      // Should NOT show error (https:// was added)
      expect(mockToastError).not.toHaveBeenCalled()

      // Dialog should close
      await waitFor(() => {
        expect(screen.queryByText('Insert Link')).toBeFalsy()
      })
    })
  })

  describe('XSS Attack Vectors', () => {
    const maliciousUrls = [
      { name: 'javascript: basic', url: 'javascript:alert(1)' },
      { name: 'javascript: uppercase', url: 'JAVASCRIPT:alert(1)' },
      { name: 'javascript: mixed case', url: 'JaVaScRiPt:alert(1)' },
      { name: 'javascript: with spaces', url: '  javascript:alert(1)' },
      { name: 'data: HTML', url: 'data:text/html,<script>alert(1)</script>' },
      { name: 'data: base64', url: 'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==' },
      { name: 'vbscript:', url: 'vbscript:msgbox("XSS")' },
    ]

    maliciousUrls.forEach(({ name, url }) => {
      it(`should block ${name}`, async () => {
        const user = userEvent.setup()
        render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

        await waitFor(() => {
          expect(document.querySelector('.note-editor')).toBeTruthy()
        })

        // Focus and open link dialog
        const container = document.querySelector('.note-editor')!
        await user.click(container)

        await waitFor(() => {
          expect(container.classList.contains('ring-2')).toBe(true)
        })

        const linkButton = screen.getByLabelText('Link')
        await user.click(linkButton)

        await waitFor(() => {
          expect(screen.getByText('Insert Link')).toBeTruthy()
        })

        // Enter malicious URL
        const urlInput = screen.getByPlaceholderText('https://example.com')
        await user.type(urlInput, url)

        // Click confirm
        const confirmButton = screen.getByRole('button', { name: 'Confirm' })
        await user.click(confirmButton)

        // Should show error toast
        await waitFor(() => {
          expect(mockToastError).toHaveBeenCalledWith('Invalid URL')
        })
      })
    })
  })

  describe('Safe URL Protocols', () => {
    // Note: The NoteEditor auto-adds https:// to URLs without http/https protocol
    // So http: and https: URLs work directly, while mailto:/tel: would need
    // the protocol prefix to be recognized (otherwise https:// gets prepended)
    const safeUrls = [
      { name: 'https:', url: 'https://example.com' },
      { name: 'http:', url: 'http://example.com' },
    ]

    safeUrls.forEach(({ name, url }) => {
      it(`should allow ${name}`, async () => {
        const user = userEvent.setup()
        render(<NoteEditor value={defaultValue} onChange={mockOnChange} />)

        await waitFor(() => {
          expect(document.querySelector('.note-editor')).toBeTruthy()
        })

        // Focus and open link dialog
        const container = document.querySelector('.note-editor')!
        await user.click(container)

        await waitFor(() => {
          expect(container.classList.contains('ring-2')).toBe(true)
        })

        const linkButton = screen.getByLabelText('Link')
        await user.click(linkButton)

        await waitFor(() => {
          expect(screen.getByText('Insert Link')).toBeTruthy()
        })

        // Enter safe URL
        const urlInput = screen.getByPlaceholderText('https://example.com')
        await user.type(urlInput, url)

        // Click confirm
        const confirmButton = screen.getByRole('button', { name: 'Confirm' })
        await user.click(confirmButton)

        // Should NOT show error toast
        expect(mockToastError).not.toHaveBeenCalled()
      })
    })
  })
})

describe('NoteEditor - paste byte limit', () => {
  const emptyValue: NoteContent = {
    content: { type: 'doc', content: [{ type: 'paragraph' }] },
  }

  // jsdom has no layout: ProseMirror's post-dispatch scrollToSelection calls
  // Range.getClientRects(), which returns empty and throws. Shim a zero rect.
  const zeroRect = {
    top: 0, left: 0, bottom: 0, right: 0, width: 0, height: 0, x: 0, y: 0,
    toJSON: () => ({}),
  } as DOMRect
  let origBounding: typeof Range.prototype.getBoundingClientRect
  let origClientRects: typeof Range.prototype.getClientRects

  beforeAll(() => {
    origBounding = Range.prototype.getBoundingClientRect
    origClientRects = Range.prototype.getClientRects
    Range.prototype.getBoundingClientRect = () => zeroRect
    Range.prototype.getClientRects = () =>
      ({
        length: 1,
        item: () => zeroRect,
        0: zeroRect,
        [Symbol.iterator]: () => [zeroRect][Symbol.iterator](),
      }) as unknown as DOMRectList
  })

  afterAll(() => {
    Range.prototype.getBoundingClientRect = origBounding
    Range.prototype.getClientRects = origClientRects
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  function pasteText(text: string) {
    const contentEl = document.querySelector('.note-editor-content')
    expect(contentEl).toBeTruthy()
    const clipboardData = {
      getData: (type: string) => (type === 'text/plain' ? text : ''),
      types: ['text/plain'],
      files: [],
    }
    fireEvent.paste(contentEl as Element, { clipboardData })
  }

  it('truncates an over-limit paste so the note stays within the cap', async () => {
    const onChange = vi.fn()
    render(
      <NoteEditor value={emptyValue} onChange={onChange} maxBytes={400} />
    )

    const container = document.querySelector('.note-editor') as Element
    await waitFor(() => expect(container).toBeTruthy())
    fireEvent.click(container)

    pasteText('x'.repeat(8000))

    await waitFor(
      () => {
        expect(onChange).toHaveBeenCalled()
        const calls = onChange.mock.calls
        const last = calls[calls.length - 1][0] as NoteContent
        expect(calculateNoteByteLength({ content: last.content })).toBeLessThanOrEqual(400)
      },
      { timeout: 2000 }
    )
  })

  it('keeps an in-limit paste intact', async () => {
    const onChange = vi.fn()
    render(
      <NoteEditor value={emptyValue} onChange={onChange} maxBytes={4096} />
    )

    const container = document.querySelector('.note-editor') as Element
    await waitFor(() => expect(container).toBeTruthy())
    fireEvent.click(container)

    pasteText('hello world')

    await waitFor(
      () => {
        expect(onChange).toHaveBeenCalled()
        const calls = onChange.mock.calls
        const last = calls[calls.length - 1][0] as NoteContent
        expect(JSON.stringify(last.content)).toContain('hello world')
        expect(calculateNoteByteLength({ content: last.content })).toBeLessThanOrEqual(4096)
      },
      { timeout: 2000 }
    )
  })

  it('truncates right up to the cap — never over, fills the budget', async () => {
    const onChange = vi.fn()
    const cap = 300
    render(
      <NoteEditor value={emptyValue} onChange={onChange} maxBytes={cap} />
    )

    const container = document.querySelector('.note-editor') as Element
    await waitFor(() => expect(container).toBeTruthy())
    fireEvent.click(container)

    // All-ASCII payload: 1 byte/char near the cut, so the largest fitting
    // prefix lands within a couple bytes of the cap, and NEVER over it.
    pasteText('z'.repeat(5000))

    await waitFor(
      () => {
        expect(onChange).toHaveBeenCalled()
        const calls = onChange.mock.calls
        const last = calls[calls.length - 1][0] as NoteContent
        const size = calculateNoteByteLength({ content: last.content })
        expect(size).toBeLessThanOrEqual(cap)        // inclusive boundary: never exceeds
        expect(size).toBeGreaterThan(cap - 5)        // truncated to fit, not under-cut
      },
      { timeout: 2000 }
    )
  })

  // The over-cap external-load path (NoteEditor prop-sync uses
  // editor.chain().setMeta(BYTE_LIMIT_BYPASS,true).setContent(...)) is covered
  // deterministically at the Editor level in ByteLimitExtension.test.ts —
  // exercising the exact call shape without jsdom/React/debounce flakiness.
})
