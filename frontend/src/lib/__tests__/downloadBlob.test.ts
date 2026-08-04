import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { downloadBlob } from '../downloadBlob'

const OBJECT_URL = 'blob:mock-object-url'

describe('downloadBlob', () => {
  let createObjectURL: ReturnType<typeof vi.fn>
  let revokeObjectURL: ReturnType<typeof vi.fn>
  let appendSpy: ReturnType<typeof vi.spyOn<Node, 'appendChild'>>
  let attachedAtClick: boolean

  const appendedLink = () => appendSpy.mock.calls[0]?.[0] as HTMLAnchorElement | undefined

  beforeEach(() => {
    createObjectURL = vi.fn(() => OBJECT_URL)
    revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))

    appendSpy = vi.spyOn(document.body, 'appendChild')
    attachedAtClick = false
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      attachedAtClick = appendedLink()?.parentNode === document.body
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('clicks an anchor carrying the object URL and filename', () => {
    const blob = new Blob(['payload'], { type: 'application/gzip' })

    downloadBlob('plans-2026-01-01.danteplanner', blob)

    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(appendedLink()?.tagName).toBe('A')
    expect(appendedLink()?.getAttribute('href')).toBe(OBJECT_URL)
    expect(appendedLink()?.getAttribute('download')).toBe('plans-2026-01-01.danteplanner')
  })

  it('has the anchor in the document when clicked and removes it afterwards', () => {
    downloadBlob('file.danteplanner', new Blob(['x']))

    expect(attachedAtClick).toBe(true)
    expect(document.body.contains(appendedLink() ?? null)).toBe(false)
  })

  it('revokes the object URL it created', () => {
    downloadBlob('file.danteplanner', new Blob(['x']))

    expect(revokeObjectURL).toHaveBeenCalledWith(OBJECT_URL)
  })
})
