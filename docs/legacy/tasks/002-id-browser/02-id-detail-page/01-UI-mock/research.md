# Research: Identity Detail Page UI Mock

## Overview of Scoped Files

- IdentityDetailPage currently placeholder with only ID parameter and back button
- Uses TanStack Router for routing with id parameter from URL path
- IdentityCard shows layered image composition pattern useful for skill image composite
- Existing utility functions in identityUtils for image path generation
- Identity interface defines basic data: id, name, star, sinner, traits, keywords
- useIdentityData hook loads identity spec from JSON and merges with names
- identitySpecList JSON has minimal fields: star, sinner, traits, keywords with brackets
- parseBracketNotation utility removes brackets from game data values
- Button component from shadcn/ui imported for navigation
- i18n integration via useTranslation hook available

## File Relationships

- IdentityDetailPage receives id from URL via useParams hook
- IdentityCard links to detail page using TanStack Router Link component
- identityUtils provides image path helpers that detail page will need
- useIdentityData hook will fetch identity by ID for detail display
- Identity interface needs extension for full detail page data fields
- Current spec JSON lacks most fields required by mockup layout
- Image layering from IdentityCard applicable to skill composite layers

## Implementation Considerations

- Layout divided into four quadrants: header top-left, skills top-right, sanity bottom-left, passives bottom-right
- Status panels are three horizontal panels in a row not vertical stack
- Skills panel has purple skill selector at top with 4 buttons switching skill content
- Skill info displayed as horizontal row: name pill plus base power plus attack weight
- Image composite requires multiple layers with overlays for attack type and power values
- Coin display needs interpretation of coin sequence format
- Sanity panel has three vertical rows with small colored icon boxes on left
- Passive sections can have multiple entries stacked vertically
- Trait tags display from bracket-notated array values
- Global level setting needed for skill calculations across site
- Additional utility functions needed for resistance icons attack type icons panic icons
- Multi-line text support required for identity name and descriptions
- Pill-style backgrounds needed for skill names and passive names
- Current data structure completely insufficient for mockup requirements
