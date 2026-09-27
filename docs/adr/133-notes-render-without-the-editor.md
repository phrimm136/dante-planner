# 133 notes-render-without-the-editor
epic: none · pr: none

## Decisions
- @notes @read-path — Read-only pages render note content with a small renderer that maps the note JSON's fixed node and mark set (paragraph, heading 1–3, bullet and ordered lists, bold, italic, strike, code, link, spoiler, hard break) to React elements; the rich-text editor loads only where a note is edited. Rendering through the editor put Tiptap and ProseMirror, about 360 KB raw, on every detail page's critical path to show text; the renderer cuts each detail route by about 357 KB raw / 109 KB gzip net, after DOMPurify's 27 KB joins the read route for link checks, and `@tiptap/html` removed none of it because `@tiptap/core` imports ProseMirror's view layer.
  REJECTED: `@tiptap/html` static rendering — measured 0% of editor bytes removed as built, 4–7% with the Tiptap chunk group split.
  REJECTED: keeping the editor in read-only mode — the cost the decision exists to remove.
- @notes @parity — The renderer's output is pinned to the editor's by a test over a fixture holding every supported node and mark, recorded from the editor's own `getJSON()`; adding a node or mark to the editor fails that test until the renderer handles it. Links keep the editor's attributes and pass through the user-HTML sanitizer's URL rules.
  REJECTED: rendering unknown nodes silently as text — a new editor feature would degrade on read pages with no signal.

## Takeaway
- takeaway: an editor is the heaviest possible way to display the document it edits; a closed schema is cheap to render by hand once a parity test holds the two together.
