# Task: Connect to JSON Files

## Description:
Connect the components' name to the corresponding JSON files - texts, images. Move the acquire section to the first column - below the cost. Then the column will be 2.

## Research
- gift image - `/static/images/gift/{id}.webp`
- gift name - `/static/i18n/{lang}/{id}.json` - `name`
- gift cost - `/static/data/EGOGiftSpecList.json` - `{id}/cost`
- descs - `/static/i18n/{lang}/{id}.json` - `descs` (variable elements)
- acquire - `/static/i18n/{lang}/{id}.json` - `acq`
- Note that some gifts have no enhancement, some with one, the others with two. The enhancement panel have to show only possible enhancements.