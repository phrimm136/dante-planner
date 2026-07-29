# Task: Light/Dark Mode

## Description
Support light/dark mode. Pressing the theme button on the header switches the theme and the icon shape (sun <-> moon). If possible, read the browser's light/dark mode config and follow it to choose one of the themes as the default.

## Research
- Try to read the browser configuration (chromium, firefox, safari); if failed, set the dark theme as default
- Shift between two different themes and button shapes

## Scope
- frontend/components/Header.tsx
- frontend/styles/globals.css

## Target Code Areas
- frontend/src/components
- frontend/src/routes

## Testing Guidelines
- The developer tests the functionality of the theme switch
- The development environment turned on dark mode; the dark theme must be default