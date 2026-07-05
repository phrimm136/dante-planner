import { Mark, mergeAttributes } from '@tiptap/core'

/**
 * Spoiler Extension for Tiptap
 *
 * Creates a mark that wraps text in a spoiler element.
 * Spoiler text is hidden (blurred/obscured) and revealed on hover or click.
 *
 * Usage in editor:
 * - Toggle via toolbar button or keyboard shortcut (Ctrl/Cmd+Shift+S)
 * - Selected text becomes spoiler, or typing continues as spoiler
 *
 * HTML output: <span class="spoiler" data-spoiler="true">hidden text</span>
 *
 * CSS should be added to style the spoiler:
 * .spoiler { background: currentColor; border-radius: 2px; }
 * .spoiler:hover, .spoiler.revealed { background: transparent; }
 */

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    spoiler: {
      /**
       * Set spoiler mark
       */
      setSpoiler: () => ReturnType
      /**
       * Toggle spoiler mark
       */
      toggleSpoiler: () => ReturnType
      /**
       * Unset spoiler mark
       */
      unsetSpoiler: () => ReturnType
    }
  }
}

export const SpoilerExtension = Mark.create({
  name: 'spoiler',

  // Can coexist with other marks (bold, italic, etc.)
  inclusive: true,

  // Allow spanning across multiple nodes
  spanning: true,

  // Parse from HTML
  parseHTML() {
    return [
      {
        tag: 'span[data-spoiler]',
      },
      {
        tag: 'span.spoiler',
      },
    ]
  },

  // Render to HTML
  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        class: 'spoiler',
        'data-spoiler': 'true',
      }),
      0, // 0 means content goes here
    ]
  },

  // Add commands
  addCommands() {
    return {
      setSpoiler:
        () =>
        ({ commands }) => {
          return commands.setMark(this.name)
        },
      toggleSpoiler:
        () =>
        ({ commands }) => {
          return commands.toggleMark(this.name)
        },
      unsetSpoiler:
        () =>
        ({ commands }) => {
          return commands.unsetMark(this.name)
        },
    }
  },

  // Add keyboard shortcut
  addKeyboardShortcuts() {
    return {
      'Mod-Shift-s': () => this.editor.commands.toggleSpoiler(),
    }
  },
})

export default SpoilerExtension
