# Task: Multi-purpose Search Bar

## Description
Replace the identity search bar placeholder to a functioning one. Its behavior is slightly tricky; given the general bracketed keyword and language-wise mapping json files, the search bar have to take natural language text, convert them into the bracketed keyword, and then query them for the result. Note that it have to support partial matching. For example, if a Korean user wants to find identities with a keyword `[rupture]`, he/she put a character `파`, which is a part of `파열`, the Korean word corresponding to rupture. Then, the website have to match the character to the given keywords, and then return identities with the rupture keyword. Same as `traits`. `Name` works differently; direct match from `/static/i18n/{language}/identityNameList.json`

## Research
- How to match the bracketed keywords given text from different languages?
- Live search - no pressing enter, just a short wait for query
- The query works on the client; no server concern
- Proper placeholder that indicates what text can be queried

## Scope
- `/static/data/identitySpecList.json`
- `/static/i18n/EN/identityNameList.json`
- `/static/i18n/EN/keywordMatch.json`
- `/static/i18n/EN/traitMatch.json`
- `/frontend/src/components/identity/`

## Target Code Area
- `/frontend/src/components/identity/IdentitySearchBar.tsx`
- `/frontend/src/components/identity/IdentityList.tsx`

## Testing Guidelines
