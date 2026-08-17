/**
 * Shared corpus for the Unity rich text readers.
 *
 * Every consumer of the tokenizer renders the same strings, so a difference
 * between two readers is a property of the reader and not of its test data.
 * Entries mix the three color-validity notions (six-digit hex, any value,
 * non-empty value), tag nesting, unclosed tags, and strings lifted from the
 * shipped i18n files.
 */

export const RICH_TEXT_CORPUS: string[] = [
  '',
  'plain text',
  'Hello world',
  'Text with <special> & "chars"',
  '<color=red>x</color>',
  '<color=#fff>x</color>',
  '<color=#aabbccdd>x</color>',
  '<color=>x</color>',
  '<color=#aa0000>a<color=#bb0000>b</color>c</color>',
  '<color=#ff0000>outer <color=#00ff00>inner</color> still outer</color>',
  '<size=75%>x</size>',
  '<s>x</s>',
  '<s>a</s>b<s>c</s>',
  '<s>unclosed',
  '<color=#d40000><s>x</s></color>',
  '<color=#111111>a</color><color=#222222>b</color>',
  '<color=#ff0000>x',
  '<color=#ff0000></color>',
  'before <color=#00ff00>green</color> after',
  '<color=#ff0000>text</color> </color=#ebcaa2>more</color>',
  'x</color>y',
  'a\nb',
  '<size=95%>a\nb</size>',
  '<color=red>a\nb</color>',
  '<style="upgradeHighlight">x</style>',
  '<style="fancy">x</style>',
  '<style="upgradeHighlight"></style>',
  '<style="upgradeHighlight">a [Sinking] b</style> tail',
  '<color=#d40000><s>Jia Family</s></color>',
  'サ<size=50%>ル</size>党派',
  '<color=#a16a3b>a</color>\n<size=75%><color=#a16a3b>b</color></size>',
  '<color=red>Decreases when hit by an enemy\n<size=95%>(Base Value is 1, raised by up to 2 per Turn)</size></color>',
  'At 30 or higher SP, decreases after winning a Clash\n<size=95%>(Base Value is 1, raised by <color=red>2%</color> per Clash count after 1)</size>',
]

/**
 * Size tags that no shipped i18n string contains: unbalanced pairs and nested
 * pairs. Every `<size=…>` in the game data is a single balanced pair, so these
 * exercise the fallback path where a size tag stays literal text.
 */
export const MALFORMED_SIZE_CORPUS: string[] = [
  '<size=75%>x',
  '</size>orphan',
  '<size=1><size=2>x</size></size>',
]
