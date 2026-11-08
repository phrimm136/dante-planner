export const STATIC_SERVED_DIRS: readonly string[] = ['images', 'data', 'i18n']
// Well-known files that must be served at stable root paths, outside the hashed pipeline
export const STATIC_ROOT_FILES: readonly string[] = [
  'sitemap.xml',
  'robots.txt',
  'favicon.ico',
  '_headers',
]
