# 076 bookmark-feature-removal
epic: none · pr: none

## Decisions

- @bookmark — The bookmark feature is removed entirely — read side, storage table, wire field,
  and translations — because the write path never had a production caller, so the read side has
  only ever rendered false. Overturns the keep-the-read-side clause of RFC 0003's write-path
  removal ("it renders"): rendering a constant is not rendering.
  REJECTED: keeping the read-side projection — live code over a value with no writer.
- @bookmark @deploy — The removal ships frontend and backend in one big-bang release; browsers
  holding the old bundle fail list parsing until reload, accepted as a bounded, self-healing
  window. REJECTED: frontend-first two-step ordering — two releases of skew-safety for users who
  only need one reload.

## Takeaway

- takeaway: a read path justified by "it renders" needs a writer somewhere in its history;
  render-only code over a constant is removal debt wearing a feature's clothes.
