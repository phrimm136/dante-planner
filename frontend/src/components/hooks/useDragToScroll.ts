import { useEffect } from 'react'

const DRAG_THRESHOLD_PX = 5

const INTERACTIVE_SELECTORS =
  'a, button, input, textarea, select, [role="button"], [contenteditable="true"]'

function isScrollable(el: HTMLElement): boolean {
  const style = getComputedStyle(el)
  const overflowY = style.overflowY
  const overflowX = style.overflowX
  const canScrollY =
    (overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight
  const canScrollX =
    (overflowX === 'auto' || overflowX === 'scroll') && el.scrollWidth > el.clientWidth
  return canScrollY || canScrollX
}

function findScrollableAncestor(el: HTMLElement): HTMLElement | null {
  let current: HTMLElement | null = el
  while (current && current !== document.documentElement && current !== document.body) {
    if (isScrollable(current)) return current
    current = current.parentElement
  }
  return null
}

function isInteractiveTarget(target: HTMLElement): boolean {
  return !!target.closest(INTERACTIVE_SELECTORS)
}

/**
 * Global drag-to-scroll hook.
 *
 * Attaches document-level mouse listeners that detect scrollable panes
 * and enable click-drag scrolling. Ignores interactive elements (buttons,
 * inputs, links) and the root page scroll.
 *
 * Call once in GlobalLayout — no per-component wiring needed.
 */
export function useDragToScroll() {
  useEffect(() => {
    let isDragging = false
    let hasMoved = false
    let startX = 0
    let startY = 0
    let scrollLeft = 0
    let scrollTop = 0
    let container: HTMLElement | null = null

    const onMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return
      const target = e.target as HTMLElement
      if (isInteractiveTarget(target)) return

      container = findScrollableAncestor(target)
      if (!container) return

      isDragging = true
      hasMoved = false
      startX = e.clientX
      startY = e.clientY
      scrollLeft = container.scrollLeft
      scrollTop = container.scrollTop
    }

    const onMouseMove = (e: MouseEvent) => {
      if (!isDragging || !container) return

      const dx = e.clientX - startX
      const dy = e.clientY - startY

      if (!hasMoved && Math.abs(dx) < DRAG_THRESHOLD_PX && Math.abs(dy) < DRAG_THRESHOLD_PX) {
        return
      }

      if (!hasMoved) {
        hasMoved = true
        container.style.cursor = 'grabbing'
        container.style.userSelect = 'none'
      }

      container.scrollLeft = scrollLeft - dx
      container.scrollTop = scrollTop - dy
    }

    const onMouseUp = () => {
      if (container && hasMoved) {
        container.style.cursor = ''
        container.style.userSelect = ''
      }
      isDragging = false
      container = null
      // Delay reset so the capturing click handler can still read hasMoved
      if (hasMoved) {
        setTimeout(() => {
          hasMoved = false
        }, 0)
      }
    }

    // Suppress click events that fire after a drag gesture
    const onClickCapture = (e: MouseEvent) => {
      if (hasMoved) {
        e.stopPropagation()
        e.preventDefault()
      }
    }

    document.addEventListener('mousedown', onMouseDown)
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
    document.addEventListener('click', onClickCapture, true)

    return () => {
      document.removeEventListener('mousedown', onMouseDown)
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
      document.removeEventListener('click', onClickCapture, true)
    }
  }, [])
}
