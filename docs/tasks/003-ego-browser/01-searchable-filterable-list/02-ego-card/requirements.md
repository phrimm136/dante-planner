# Task: EGO Card

## Description
Create an EGO card. The EGO card is consisted of an EGO image and EGO frame over the image. On the top of the image set, there are sinner icon and its background icon like those of 1-star identity card. On the bottom, there is ego info panel with sin color. The panel is consisted of three sections. The middle section shows the ego name, the left for its rank icon, the right for its threadspin tier (defaults to four - can be modified later). Right up to the panel, there is another rank indicator. Note that the threadspin tier icon is tilted toward upper-right. Clicking the card moves the page to the EGO detail page, `/ego/{id}`.

## Research
- The size of ego art and frame have to be the same.
- Adding ego panel to the sin color cache initialization can make the site slower; can cloudflare cdn create the colored images in response?
- 