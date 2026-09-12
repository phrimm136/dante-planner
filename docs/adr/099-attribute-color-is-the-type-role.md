# 099 attribute-color-is-the-type-role
epic: none · pr: none · supersedes: 098 @color @attribute

## Decisions
- @color @attribute — The one color the frontend paints an attribute in is the `type` role, unmodified; an attribute without a `type` entry (NONE) gets the neutral gray fallback. The client's skill tab button caches `UIColorManager.GetAttributeTypeColor` and its skill name plate receives the frame list's entry, and the two lists carry the same values for every attribute that has both. REJECTED: `background`, the role nearest the previously shipped tints — those tints were a derivation of unknown origin, and matching them reproduced a look the game never had.
- @color @skillPlate — The skill name banner keeps its shape (angled left cut, diagonal stripes at the right) and takes its colors from the client's plate: the stripes are the type color at full coverage and the body is 0.33 of it, the floor the client's plate sprite ramps down to at its right end. The client tints one white sprite whose texels carry that ramp; there is no second image, group alpha, or multiplier. REJECTED: redrawing the banner as the client's plate with its ramp and underline — the banner's shape is a deliberate house design.
- @color @skillTab — Hover and selected each paint the type color at 0.5 coverage over the muted panel, and hover on a selected tab paints 0.75, the coverage two 0.5 overlays reach when stacked. The client fades two identically colored overlay images whose sprite alpha is 0.5, one per state, and both are visible when both states hold. REJECTED: darkening the fill for the selected state and showing the plain color on hover — the client never multiplies the color.

## Takeaway
- takeaway: when the reference is a running program, read what it does rather than fit what it used to look like; nearest-match to a drifted copy converges on the copy.
