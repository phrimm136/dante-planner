# 100 filter-store-lifetime
epic: none · pr: none · supersedes: 043 @filter @state

## Decisions
- @filters @lifetime — Each list slice owns one filter store built at module scope (`createFilterStore` in its `stores/` folder), and the page subscribes through `useFilterStore`. A store created inside the page's render dies with the route component, so a list → detail → back trip silently drops every facet and the search query the user had set. REJECTED: keeping the store per mount and persisting it to sessionStorage — a second copy of the state with its own serialization for `Set`s, and a stale-read window on every mount. REJECTED: a keep-mounted layout route holding the list — it pins every list page's DOM and its card subscriptions in memory for the whole session to preserve a few `Set`s.
- @filters @reset — `resetAll` writes `store.getInitialState()` back. The initial record is the slice's declared empty state, so the reset needs no per-key knowledge and cannot drift as facets are added. REJECTED: clearing each registered key to `new Set()` — it silently redefines "reset" for any facet whose initial value is not empty.
- @filters @scope — The store's lifetime is the module's: filters survive navigation but not a reload, and every tab has its own. Nothing in the URL or storage records them. REJECTED: mirroring the facets into search params — it makes every filter toggle a navigation and turns the facet record into a public URL contract.

## Takeaway
- takeaway: state whose lifetime must exceed a component's belongs to a module the component reads, not to a hook it calls; moving the creation out of render is cheaper than any scheme for carrying it across mounts.
