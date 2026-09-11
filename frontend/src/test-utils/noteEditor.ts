import { beforeAll, afterAll } from 'vitest'
import { act, fireEvent } from '@testing-library/react'

/**
 * jsdom has no layout: ProseMirror's post-dispatch scrollToSelection calls
 * Range.getClientRects(), which returns empty and throws. Shims a zero rect for
 * the enclosing suite.
 */
export function stubRangeRects(): void {
  const zeroRect = {
    top: 0,
    left: 0,
    bottom: 0,
    right: 0,
    width: 0,
    height: 0,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  } as DOMRect
  const originals = new Map<string, PropertyDescriptor | undefined>()

  beforeAll(() => {
    for (const name of ['getBoundingClientRect', 'getClientRects']) {
      originals.set(name, Object.getOwnPropertyDescriptor(Range.prototype, name))
    }
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
    for (const [name, descriptor] of originals) {
      if (descriptor) Object.defineProperty(Range.prototype, name, descriptor)
      else delete (Range.prototype as unknown as Record<string, unknown>)[name]
    }
  })
}

/** Put text into a mounted note editor, as a paste jsdom can carry. */
export function pasteIntoNote(container: Element, text: string): void {
  fireEvent.focusIn(container)
  const contentEl = container.querySelector('.note-editor-content')
  if (!contentEl) throw new Error('the container holds no note editor content')
  fireEvent.paste(contentEl, {
    clipboardData: {
      getData: (type: string) => (type === 'text/plain' ? text : ''),
      types: ['text/plain'],
      files: [],
    },
  })
}

/** Run the microtask the write-through scheduled, and nothing later. */
export async function flushMicrotask(): Promise<void> {
  await act(async () => {
    await Promise.resolve()
  })
}
