# Implementation Plan: Header Layout

## Task Overview

Transform the current minimal Header component into a comprehensive navigation header with three distinct sections arranged horizontally. The header will include a clickable title/logo on the left, navigation links in the center, and settings buttons on the right. This layout will provide the primary navigation structure for Dante's Planner application.

**Current State**: Header displays only "Dante's Planner" title with basic padding.

**Target State**: Fully-featured header with:
- **Left Section**: Clickable title linking to homepage
- **Center Section**: Navigation menu (In-Game Info, Planner, Community)
- **Right Section**: Settings controls (Language, Theme Toggle, Settings, Sign In)

**Key Constraint**: Settings buttons will be visual placeholders without functionality, as the underlying systems (i18n, theme switching, authentication) are not yet implemented.

---

## Steps to Implementation

### Step 1: Setup Imports and Prepare Component Structure

**What**: Add necessary imports and set up the basic three-section layout structure.

**Actions**:
- Import TanStack Router Link component
- Import Button component from shadcn UI
- Import icons from lucide-react (Languages, Sun, Moon, Settings, User)
- Import cn utility (if needed for future enhancements)
- Replace current simple header structure with flexbox container

**Rationale**: Gather all dependencies before implementing layout to ensure clean, organized code.

**Deliverable**: Component file with all imports and basic container structure using `flex items-center justify-between`.

---

### Step 2: Implement Left Section (Title/Logo)

**What**: Create the leftmost section with a clickable title that navigates to the root page.

**Actions**:
- Wrap "Dante's Planner" text in TanStack Router Link component
- Link to="/" for homepage navigation
- Style the link to look like plain text (remove underline, use text-foreground color)
- Add hover state with transition to text-primary color
- Keep existing text-2xl font-bold styling for the title
- Wrap in a div with flex-shrink-0 to prevent squashing

**Styling**:
- No underline by default
- Subtle color change on hover
- Smooth transition for hover effect
- Maintains current visual prominence of title

**Deliverable**: Clickable title on the left that navigates to homepage with proper hover states.

---

### Step 3: Implement Center Section (Navigation Links)

**What**: Create navigation menu with three links using proper spacing.

**Actions**:
- Create nav element with `flex items-center gap-6`
- Add three navigation links:
  1. "In-Game Info" → `/info` (placeholder route)
  2. "Planner" → `/planner` (placeholder route)
  3. "Community" → `/community` (placeholder route)
- Use Button component with asChild prop and variant="ghost"
- Wrap each Button with TanStack Router Link
- Add TODO comments noting routes need to be created

**Pattern**:
```
<Button asChild variant="ghost">
  <Link to="/planner">Planner</Link>
</Button>
```

**Spacing**: gap-6 (24px) between navigation items for generous, readable spacing.

**Rationale**:
- Button + Link composition provides consistent styling
- Ghost variant keeps navigation subtle and clean
- Placeholder routes are acceptable for layout implementation
- Routes will be created in future tasks

**Deliverable**: Three styled navigation links in center with proper spacing, linking to placeholder routes.

---

### Step 4: Implement Right Section (Settings Buttons)

**What**: Create settings controls area with four icon buttons.

**Actions**:
- Create div with `flex items-center gap-2` for compact grouping
- Add flex-shrink-0 to prevent squashing
- Add four icon buttons using Button component with size="icon" and variant="ghost":
  1. **Language Selector**: Languages icon, aria-label="Select language"
  2. **Theme Toggle**: Sun icon (or Moon for dark mode), aria-label="Toggle theme"
  3. **Settings**: Settings icon, aria-label="Settings"
  4. **Sign In**: User icon, aria-label="Sign in"
- Add TODO comments for each button noting future implementation needed
- Ensure all buttons have proper aria-labels for accessibility

**Spacing**: gap-2 (8px) between icon buttons for compact, cohesive grouping.

**Considerations**:
- No onClick handlers (placeholders)
- Icons should be visually clear and recognizable
- Accessibility labels essential for icon-only buttons
- May consider adding disabled state or tooltips in future

**Deliverable**: Four styled icon buttons in right section with proper accessibility labels.

---

### Step 5: Verify Layout Proportions and Spacing

**What**: Review and adjust the three-section layout to ensure proper proportions and spacing.

**Actions**:
- Verify left section (title) has natural width and doesn't shrink
- Verify center section (navigation) has adequate space and is visually centered
- Verify right section (settings) is compact and aligned to the right
- Check overall spacing between sections (gap-4 on main container)
- Ensure vertical alignment is consistent (items-center)
- Test that layout doesn't break with different title lengths

**Proportions**:
- Left: ~150-250px (natural width of "Dante's Planner")
- Center: Flexible, accommodates three nav links with gap-6
- Right: ~150-200px (four icon buttons with gap-2)
- Total expected width: ~600-800px content

**Layout Strategy**: justify-between distributes space automatically, pushing left section to start, right section to end, and centering navigation in available space.

**Deliverable**: Well-proportioned header with balanced visual weight across three sections.

---

### Step 6: Add Documentation Comments

**What**: Document placeholder functionality and future enhancement areas.

**Actions**:
- Add component-level comment explaining header structure
- Add TODO comments for each placeholder button:
  - "TODO: Implement language selection with i18n library"
  - "TODO: Implement theme toggle with theme context"
  - "TODO: Link to settings page when created"
  - "TODO: Implement authentication with OAuth 2.0"
- Add TODO comment for navigation routes:
  - "TODO: Create actual routes for In-Game Info, Planner, and Community pages"
- Add comment noting responsive design is future enhancement
- Document that header relies on GlobalLayout wrapper for bg-card and border

**Rationale**: Clear documentation helps future developers understand what's implemented vs placeholder, and what needs to be built next.

**Deliverable**: Well-documented component with clear TODOs for future work.

---

### Step 7: Manual Testing

**What**: Verify the header layout works correctly and meets requirements.

**Actions**:
1. Start dev server (yarn dev)
2. Visually inspect header layout in browser
3. Test title link - should navigate to homepage (/)
4. Test navigation links - should show 404 pages (expected, routes don't exist)
5. Verify all elements are in correct positions (left/center/right)
6. Check spacing and proportions look balanced
7. Verify icon buttons are visible and properly spaced
8. Test hover states on title and navigation links
9. Check that layout doesn't break at different browser widths (basic check)
10. Use browser DevTools to verify applied CSS classes
11. Check accessibility in DevTools (aria-labels present)

**Testing Guidelines** (from instructions.md):
- Title should be in the leftmost position
- Navigation links should be next to title with proper spacing
- Settings buttons should be in the rightmost position
- All components should be in their specified locations

**Expected Behavior**:
- ✅ Title links to homepage
- ✅ Navigation links show (but routes 404 - acceptable)
- ✅ Icon buttons visible but non-functional (expected)
- ✅ Proper spacing and alignment
- ✅ Hover states work on interactive elements
- ⚠️ Settings buttons don't do anything (placeholder - expected)

**Deliverable**: Verified, working header layout meeting all positional requirements.

---

### Step 8: TypeScript Compilation Check

**What**: Ensure no TypeScript errors in the implementation.

**Actions**:
- Run `yarn tsc --noEmit` to check for type errors
- Verify all imports are correctly typed
- Confirm Button component types work with asChild prop
- Ensure Link component types are correct

**Expected Outcome**: No TypeScript errors in Header.tsx (pre-existing test-utils errors may still exist, but are unrelated).

**Deliverable**: TypeScript-compliant implementation with no type errors.

---

## Timeline

| Step | Description | Estimated Time | Cumulative |
|------|-------------|----------------|------------|
| 1 | Setup imports and structure | 5 minutes | 5 min |
| 2 | Implement left section (title) | 5 minutes | 10 min |
| 3 | Implement center section (nav) | 10 minutes | 20 min |
| 4 | Implement right section (settings) | 10 minutes | 30 min |
| 5 | Verify proportions and spacing | 5 minutes | 35 min |
| 6 | Add documentation comments | 5 minutes | 40 min |
| 7 | Manual testing | 10 minutes | 50 min |
| 8 | TypeScript check | 5 minutes | 55 min |

**Total Estimated Time**: 55 minutes

---

## Success Criteria

The implementation will be considered successful when:

1. ✅ Header has three distinct sections (left/center/right)
2. ✅ Title is clickable and navigates to homepage
3. ✅ Three navigation links are present and styled consistently
4. ✅ Four settings icon buttons are present and accessible
5. ✅ Proper spacing between all elements:
   - gap-4 between major sections
   - gap-6 between navigation links
   - gap-2 between icon buttons
6. ✅ All elements are in correct positions per requirements
7. ✅ Icon buttons have proper aria-labels for accessibility
8. ✅ Hover states work on interactive elements
9. ✅ No TypeScript errors in implementation
10. ✅ Dev server runs without errors
11. ✅ Code is well-documented with TODO comments
12. ✅ Layout uses semantic HTML (header, nav elements)

---

## Implementation Notes

### Spacing and Proportions

**Main Container**:
- Use `flex items-center justify-between gap-4` for three-section layout
- justify-between automatically distributes space

**Left Section (Title)**:
- flex-shrink-0 prevents squashing
- Natural width based on text length

**Center Section (Navigation)**:
- gap-6 for generous spacing between links
- Grows to accommodate nav items

**Right Section (Settings)**:
- gap-2 for compact icon button grouping
- flex-shrink-0 maintains fixed width
- flex items-center for alignment

### Color and Style Tokens

**From research.md recommendations**:
- Text colors: text-foreground (default), text-primary (hover/active)
- Background: Provided by GlobalLayout wrapper (bg-card)
- Buttons: variant="ghost" for subtle appearance
- Transitions: transition-colors for smooth hover effects

### Component Patterns

**Button + Link Composition**:
```
<Button asChild variant="ghost">
  <Link to="/path">Label</Link>
</Button>
```

**Icon Button Pattern**:
```
<Button variant="ghost" size="icon" aria-label="Description">
  <IconComponent />
</Button>
```

### Accessibility

- All icon buttons must have aria-label attributes
- Use semantic HTML elements (header, nav)
- Maintain keyboard navigation (Button and Link components handle this)
- Focus-visible states provided by Button component

### Future Enhancements (Out of Scope)

**Not included in this implementation**:
- Responsive mobile layout (hamburger menu)
- Active route highlighting
- Theme toggle functionality
- Language selection functionality
- Settings page functionality
- Authentication functionality
- Actual navigation route pages

**These will be separate tasks**:
- Theme toggle implementation (context + logic)
- i18n setup and language switching
- Authentication system integration
- Creating navigation pages (In-Game Info, Planner, Community)
- Responsive mobile menu
- Settings page

---

## Dependencies

**Required**:
- ✅ Button component (already exists)
- ✅ TanStack Router Link (already integrated)
- ✅ lucide-react icons (already installed)
- ✅ cn utility (already available)
- ✅ Theme tokens (already defined)

**Not Required (Placeholders)**:
- ❌ Theme context/state
- ❌ i18n system
- ❌ Auth context/state
- ❌ Navigation page routes

---

## Risk Assessment

**Low Risk**:
- Layout implementation (clear patterns available)
- Using existing Button component (well-tested)
- TanStack Router Link usage (established pattern)

**Medium Risk**:
- Placeholder routes may be confusing (mitigated by TODO comments)
- Non-functional buttons may seem broken (mitigated by documentation)

**No Risk**:
- No backend integration required
- No state management needed
- No external API calls

**Mitigation Strategies**:
- Clear TODO comments explaining placeholders
- Document which features are visual only
- Consider adding tooltips for placeholder buttons (future)

---

## Post-Implementation Tasks

After header layout is complete, the following tasks should be prioritized:

1. **High Priority**:
   - Create navigation page routes (In-Game Info, Planner, Community)
   - Implement theme toggle functionality
   - Set up i18n system for language support

2. **Medium Priority**:
   - Add responsive mobile menu
   - Implement active route highlighting
   - Add tooltips to icon buttons

3. **Low Priority**:
   - Optimize performance with React.memo
   - Add animation transitions
   - Create settings page

---

## Summary

This implementation will deliver a visually complete, well-structured header layout that provides the navigation foundation for Dante's Planner. While settings buttons are placeholders, the layout structure, spacing, and visual design will be production-ready. The implementation follows established patterns from the codebase and maintains consistency with the existing design system.

**Key Deliverable**: A horizontal header layout with three sections, proper proportions, and placeholder functionality for future feature integration.
