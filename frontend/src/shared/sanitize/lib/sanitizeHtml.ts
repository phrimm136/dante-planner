/**
 * The single place DOMPurify is configured.
 *
 * Default DOMPurify keeps `<style>` and inline style attributes, which is enough
 * to float a transparent full-viewport anchor over a public planner page and
 * hijack every click. User HTML here only ever comes from the tiptap editors, so
 * the allowlist is the editor schema and nothing else survives.
 */

import DOMPurify from 'dompurify'

/**
 * Tags the comment and note editors can produce: StarterKit's nodes and marks,
 * plus the spoiler mark's span.
 */
const TIPTAP_ALLOWED_TAGS = [
  'p',
  'br',
  'hr',
  'h1',
  'h2',
  'h3',
  'ul',
  'ol',
  'li',
  'blockquote',
  'pre',
  'code',
  'strong',
  'b',
  'em',
  'i',
  's',
  'del',
  'u',
  'a',
  'span',
] as const

/** Attributes those tags carry: link targets and the spoiler mark's hooks. */
const TIPTAP_ALLOWED_ATTR = ['href', 'target', 'rel', 'class', 'data-spoiler'] as const

/**
 * Sanitize editor-authored HTML for rendering.
 *
 * @param html - Untrusted HTML from a user
 * @returns HTML carrying only editor-schema tags and attributes
 */
export function sanitizeUserHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [...TIPTAP_ALLOWED_TAGS],
    ALLOWED_ATTR: [...TIPTAP_ALLOWED_ATTR],
    FORBID_TAGS: ['form', 'input', 'style'],
    FORBID_ATTR: ['style'],
    // Removing the tag is not enough: DOMPurify keeps a dropped element's text,
    // so a style or script body would survive as visible page text.
    FORBID_CONTENTS: ['script', 'style'],
  })
}

/**
 * Strip every tag, leaving the text content.
 *
 * @param html - Untrusted HTML from a user
 * @returns The text content, with no markup
 */
export function sanitizeToPlainText(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: [], FORBID_CONTENTS: ['script', 'style'] })
}
