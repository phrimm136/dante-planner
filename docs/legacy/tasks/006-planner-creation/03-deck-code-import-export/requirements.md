# Task; Deck Code Import and Export

## Description
Create a deck code import/export algorithm.
The decoded deck code is 560-length bit string. Each sinner has 46bits (total 12 sinners). The structure of sinner-wise code is (from 1):
- 1-8 bit: identity id (begins from 1)
- 9-12 bit: deployment order
- 13-19 bit: ZAYIN ego id
- 20-26 bit: TETH ego id
- 27-33 bit: HE ego id
- 34-40 bit: WAW ego id
- 41-46 bit: ALEPH ego id
Concatenating the sinner-wise codes and filling zeros until 560-length, and encoding the result by base64, gzip, and base64 again, we get a encoded deck code. Pressing export button creates this encoded deck code and copy it into the clipboard. Pressing import button reads the clipboard and try to decode the text. If the text is deck code, pop-up confirmation box to apply the deck code. If the user press yes, then apply it. If the clipboard text is not code, notify the user that the page cannot import the deck code. Place i18n-compatible import and export button that conducts the mentioned functions left to the reset order button in the deck builder.

## Research
- What is the best way to notify the users the import error without interfering them?

## Scope
- `/frontend/src/components/deckBuilder/DeckBuilder.tsx`

## Target Code Area
- `/frontend/src/components`
- `/frontend/src/components/deckBuilder/DeckBuilder.tsx`

## Testing Guidelines