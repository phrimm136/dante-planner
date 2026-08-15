# 083 jsx-a11y-enforcement-scope
epic: rfc-0004 · pr: none

## Decisions

- @lint @a11y — The `jsx-a11y` plugin lands enabled with its findings already at zero, as one
  change rather than a rule flipped on ahead of the work. A rule that ships red is a rule the
  next red build turns off. REJECTED: enable first and burn the findings down after — the
  interval is exactly when the rule teaches the team that it does not have to pass.

- @lint @a11y — `jsx-a11y/prefer-tag-over-role` is off globally, and it is the only rule of the
  nine that is. All four of its reports here are correct ARIA widget patterns it cannot express
  as a native tag: `role="group"` on a toolbar group, whose suggested tags
  (`fieldset`/`hgroup`/`optgroup`) carry wrong semantics and UA styling; `role="checkbox"` on two
  icon-filter buttons that render inline SVG a void `<input>` cannot contain; and `role="combobox"`
  on a Radix popover trigger, which `<input>`/`<select>` cannot be. The rule is advisory shaped
  like correctness. REJECTED: four file-scoped overrides — they would suppress the rule at every
  site it fires on, which enforces nothing while reading as enforcement. REJECTED: rewriting the
  widgets to native tags — it breaks rendering at three sites and changes assistive-technology
  semantics at the other, to satisfy a preference rather than a defect.

- @a11y @editor (taste) — The note editor decouples editability from focus: ProseMirror is
  `editable` whenever the note is not read-only, and focus drives only the toolbar and the ring.
  Gating `editable` on focus left an unfocused surface `contenteditable="false"`, so no keyboard
  user could reach the editor at all — the lint finding was a real trap, not ceremony. REJECTED:
  an overlay activation button, the pattern used for the other nested-interactive cards — it
  blocks the spoiler hover-reveal and text selection in preview, which is every note's default
  state. REJECTED: a file override — it would have hidden a keyboard trap behind a suppression.
  Accepted cost: clicking the container's non-text chrome no longer activates the editor.

## Takeaway

- takeaway: when a lint rule fires only on code that is already correct, the honest move is to
  retire the rule once and say why, not to grant it an exemption everywhere it looks.
