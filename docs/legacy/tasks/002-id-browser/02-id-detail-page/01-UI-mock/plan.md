# Implementation Plan: Identity Detail Page UI Mock

## Task Overview

Create the visual layout and component structure for the Identity Detail Page following the four-quadrant mockup design. This is a UI-only implementation using placeholder data to establish the layout, styling, and responsive structure before integrating with actual identity data. The page displays header information, skills with selector, status panels, sanity information, and passive abilities in a structured grid layout.

## Clarifications Needed

- Responsive breakpoints and mobile layout behavior for four-quadrant grid
- Skill selector interaction pattern: tabs vs buttons vs other component
- Image toggle functionality: switch between what alternate images
- Full-size icon behavior: open in modal or new tab
- Global level setting location: in settings page or on detail page itself
- Pill background styling specifics: rounded corners, padding values, colors
- Attack weight visual representation: filled squares size and spacing

## Assumptions Made

- Using placeholder/mock data for all content initially
- Four-quadrant layout uses CSS Grid at desktop breakpoints
- Skill selector defaults to Skill 1 on page load
- Image toggle switches between uptie levels or alternate portraits
- Full-size icon opens character image in new browser tab
- Global level setting will be separate settings page feature for now
- Pill backgrounds use theme-appropriate rounded backgrounds with padding
- Attack weight squares are inline block elements with border styling
- Three horizontal status panels collapse to vertical on mobile
- Multi-line identity name uses standard text wrapping

## Steps to Implementation

1. **Create four-quadrant grid layout**: Set up IdentityDetailPage component with CSS Grid creating four sections for header, skills, sanity, and passives using responsive breakpoints

2. **Build header area components**: Create grade icon display, multi-line identity name text, character portrait with image toggle and full-size icons as overlay buttons

3. **Implement three horizontal status panels**: Create reusable panel component for Status, Resistance, and Stagger Thresholds displaying icon-text pairs and formatted data

4. **Add traits panel**: Create space-separated tag display parsing bracket-notated trait values from data array

5. **Create skill selector with four buttons**: Build tab-like selector component with Skill 1, Skill 2, Skill 3, Defense buttons managing active state

6. **Build skill display section**: Create layered skill image composite area with background, artwork, frame layers plus overlays for attack type, base power, and coin modifier

7. **Implement skill info and description**: Add horizontal row with pill-styled skill name, base power display, attack weight squares, plus description container with base text and coin effect entries

8. **Create sanity panel with three rows**: Build Panic Type, Sanity Increment, and Sanity Decrement rows each with small colored icon box and text content

9. **Build passive skills sections**: Create Passive and Support Passive sections with pill-styled names, activation conditions showing sin icons, and effect descriptions supporting multiple stacked entries

10. **Apply theme styling and responsive adjustments**: Use global theme colors instead of mockup identification colors, ensure mobile-friendly layout collapsing quadrants vertically

## Success Criteria

- Four-quadrant layout displays correctly at desktop breakpoints with proper grid alignment
- Skill selector switches displayed skill content when buttons clicked with active state highlighting
- Three status panels arranged horizontally in header area below character portrait
- Character portrait shows layered images with toggle and full-size icons as interactive overlays
- Skill image composite displays multiple layers with attack type and power value overlays positioned correctly
- Coin effects display as stacked entries with icon and text pairs in description section
- Sanity panel shows three vertical rows with colored icon boxes and label text
- Passive sections display multiple entries stacked vertically with pill-styled names and formatted activation conditions
- Layout responds appropriately on mobile with quadrants stacking vertically
- All placeholder content renders without errors demonstrating complete layout structure
