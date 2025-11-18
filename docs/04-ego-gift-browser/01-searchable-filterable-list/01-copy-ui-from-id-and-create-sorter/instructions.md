# Task: Copy UI From ID and Create a Sorter

## Description
Copy the list UI from the identity one. Remove the sinner filter and create a sorter that sorts the ego gifts by tier and keyword (tier default). the sorting mechanism is tier -> keyword -> id or keyword -> tier -> id. The keyword order is [burn, bleed, tremor, rupture, sinking, poise, charge, slash, pierce, blunt, common]. The tier order is [EX, 5, 4, 3, 2, 1]. Id order is incremental. Place the sorter before the search bar. There are three categories for search: name (not converted to the lang-agnostic bracketed word), keywords (this is converted), and themePack(this also).

## Research
- Now we have to build three reverse mapping objects. How can I deal with this to reduce loading time?
- Give the order of string values

## Scope
- `/static/data/EGOGiftSpecList.json`
- `/static/i18n/{lang}/EGOGiftNameList.json`
- `/frontend/src/routes/IdentityPage.tsx`
- `/frontend/src/components/common/`
- `/frontend/src/components/identity/`