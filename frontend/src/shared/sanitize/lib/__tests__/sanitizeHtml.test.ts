/**
 * sanitizeHtml.test.ts
 *
 * The sanitizer is the standing defense for comment bodies: backend
 * sanitization of comment content is a tracked PENDING exemption, so these
 * cases pin what default DOMPurify would otherwise let through.
 */

import { describe, it, expect } from 'vitest'
import { sanitizeUserHtml, sanitizeToPlainText } from '../sanitizeHtml'

describe('sanitizeUserHtml', () => {
  it('keeps the tags the editors produce', () => {
    const html =
      '<p>text <strong>bold</strong> <em>italic</em> <s>struck</s> <code>code</code></p>' +
      '<h1>h1</h1><h2>h2</h2><h3>h3</h3><ul><li>item</li></ul><ol><li>item</li></ol>' +
      '<blockquote>quote</blockquote><pre>pre</pre><hr><br>'
    const out = sanitizeUserHtml(html)

    for (const tag of ['p', 'strong', 'em', 's', 'code', 'h1', 'h2', 'h3', 'ul', 'ol', 'li']) {
      expect(out).toContain(`<${tag}`)
    }
  })

  it('keeps the spoiler mark the note editor renders', () => {
    const out = sanitizeUserHtml('<span data-spoiler class="spoiler">hidden</span>')
    expect(out).toContain('data-spoiler')
    expect(out).toContain('spoiler')
  })

  it('drops a style element, which default DOMPurify keeps', () => {
    const out = sanitizeUserHtml('<style>a{position:fixed;inset:0}</style><p>body</p>')
    expect(out).not.toContain('<style')
    expect(out).not.toContain('position:fixed')
    expect(out).toContain('body')
  })

  it('drops the style attribute that floats an anchor over the page', () => {
    const out = sanitizeUserHtml(
      '<a href="https://evil.example" style="position:fixed;inset:0;opacity:0">x</a>',
    )
    expect(out).not.toContain('style')
    expect(out).not.toContain('position:fixed')
  })

  it('drops form and input elements', () => {
    const out = sanitizeUserHtml('<form action="https://evil.example"><input name="pw"></form>')
    expect(out).not.toContain('<form')
    expect(out).not.toContain('<input')
  })

  it('drops script and inline event handlers', () => {
    const out = sanitizeUserHtml('<script>alert(1)</script><p onclick="alert(1)">t</p>')
    expect(out).not.toContain('<script')
    expect(out).not.toContain('onclick')
  })

  it('drops an unlisted tag but keeps its text', () => {
    const out = sanitizeUserHtml('<marquee>scrolling</marquee>')
    expect(out).not.toContain('<marquee')
    expect(out).toContain('scrolling')
  })
})

describe('sanitizeToPlainText', () => {
  it('strips every tag and keeps the text', () => {
    expect(sanitizeToPlainText('<b>Ti</b>tle<script>alert(1)</script>')).toBe('Title')
  })

  it('returns an empty string for markup carrying no text', () => {
    expect(sanitizeToPlainText('<img src=x onerror=alert(1)>')).toBe('')
  })
})
