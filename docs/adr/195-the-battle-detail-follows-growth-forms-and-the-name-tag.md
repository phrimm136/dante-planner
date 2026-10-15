# 195 the-battle-detail-follows-growth-forms-and-the-name-tag
epic: none · pr: none

## Decisions
- @battle @growth — A phase that ships `growth` renders one number input per track (0..max, default max) labelled by the phase's condition names; skills, skill levels, passives, the acquired skill, level, HP, speed, resistances and skill level adders all follow the inputs, and a condition on a key that is not a track is held. An unlock condition on a passive renders where the identity passive draws its sync lock. REJECTED: shipping the max-growth kit only — the game's kit varies with the three trainings, so one state shows a unit no player meets until the end. REJECTED: a condition line under the passive name — the client shows a passive's gate at the lock position.
- @battle @forms — A unit kit with `forms` renders a Form group in the selector row listing only the forms the selected row can be fought in; a row with one form shows it pressed and disabled, a row without forms shows no group, and the selected form's phase replaces the unit phase (sprite and kit). A row change preselects the row's first form. REJECTED: three buttons with the impossible ones disabled — a fixed battle has one state, and showing the others implies a choice the player never has.
- @battle @name-tag — A shadow enemy (overridePersonalityId) titles the header with the copied identity's title on one line and the unit name as a line above it, with no sinner mark, like the client's personality name tag. REJECTED: the unit name as the title — every envy copy reads "질투 죄종?", which names nothing.
- @battle @header-fit — The header draws `sprite.file` placed by its shipped measures (mass box, centroid, drawn box) without waiting for the image to load; a phase without a sprite shows its id. REJECTED: measuring naturalWidth on load — the sprite moved after it appeared, and aliased phases read the wrong file.
- @battle @phase-map — The header background is the row's map for the selected phase (`phaseMaps`), else the row's map. REJECTED: a phase-level map — the map a phase switches to depends on the stage it is fought on.

## Takeaway
- takeaway: when the game's state depends on choices the player makes before a battle, expose the choice as an input and derive every section from it, rather than shipping one chosen state.
