# Epic: Planner Viewer

## Description
The planner viewer has the almost same structure as that of the planner creation, with small changes.

Category and name are no longer edittable.

No id/ego list for the deck viewer. Only id/ego equipment, affinity EA, and keyword EA remain. Clicking the sinner still changes the deployment order. There exists a button with i18n text "Team" on the bottom of the deck viewer. Clicking it opens the id/ego list which functions as same as that of the planner editor. Clicking again collapses the list. On the right of the team button, there exists a button with i18n text "Copy deck code". Clicking it copies the deck code into the clipboard. There is another button called "Reset order" button, clicking it resets the deployment order.

For the start buff, the buttons for chnaging enhencement no longer exist. Only the image, name, and description remains.

For the start ego gift, show the selected keyword and then the chosen gifts in horizontal order.

For the observed ego gift, show the selected gifts in horizontal order.

This three sections can be folded at once. On the top and the bottom of this container, there exist buttons with i18n text "Hide start settings". Clicking these buttons will collapse the container and merge the two buttons into one button with i18n text "Show start settings", clicking it opens the container again.

In the skill section, there exists a skill EA viewer that is used in planner editor, but with predefined EAs by editor.

For the comprehensive ego gift, show the implemented ego gift list, which supports filtering and search, with the selected ego gift as an argument.

Each gift card in those three gift list must support showing name-description pane on hover and dimming as collected on click.

Instead of floor section, there are horizontally scrollable list of theme packs. Hovering a pack shows two buttons on the center of the pack, one for check and one for comment. Hovering also highlights the corresponding ego gifts in the comprehensive ego gift list and move them on the top of the list. Clicking the check button dims the pack and its corresponding gifts in the comprehensive ego gift section. Clicking the comment button opens a pane with the comment correpsonding to the theme pack.

Each section except category and name has title (ex. "Start Buff" for the start buff section - must be i18n). Those sections except floor section have a button on the right of the title. Clicking the button opens a pane with the corresponding comment.

On the bottom of the viewer,
