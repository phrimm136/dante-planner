# Task: Connect to JSON Files

## Description:
Connect the components' name to the corresponding JSON files - texts, images

## Research
- gift image - `/static/images/gift/{id}.webp`
- gift name - `/static/i18n/{lang}/{id}.json` - `name`
- gift cost - `/static/data/EGOGiftSpecList.json` - `{id}/cost`
- descs - `/static/i18n/{lang}/{id}.json` - `descs` (variable elements)
- acq - `/static/i18n/{lang}/{id}.json` - `acq`