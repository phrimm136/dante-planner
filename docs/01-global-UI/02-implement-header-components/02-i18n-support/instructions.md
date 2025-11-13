# Task: i18n Support

## Description
Now we will implement the i18n support so that users can select their preferring languages. The existing texts except the title, Dante's Planner, have to be replaceable with respective to the language setting.
Note that user expects that clicking the language icon will pop up a pane of language list under the icon, and clicking one of them will change the language preference.
Filling the data will handled by user; do not touch them.

## Research
- How to construct the directory structure of the i18n json files in the static folder - language first {EN, JP, KR, CN}, then purpose {Identities/, EGOs/, gifts/, keyword.json, etc.}
- Replace the existing keywords to concise names that the i18n keywords and their English version do not duplicate
- Should I reset the site language setting in head?

## Scope
- `static`
- `frontend/index.html`
- `frontend/src/components/Header.tsx`

## Testing Guidelines
- Changing the language preferences also changes the i18n texts.
- The language preference pane properly pops up.