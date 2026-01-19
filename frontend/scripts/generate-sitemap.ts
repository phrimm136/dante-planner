/**
 * Sitemap Generator
 *
 * Generates sitemap.xml from static JSON data files.
 * Run: npx tsx scripts/generate-sitemap.ts
 */

import { readFileSync, writeFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const BASE_URL = 'https://dante-planner.com'
const STATIC_DATA_DIR = resolve(__dirname, '../../static/data')
const OUTPUT_PATH = resolve(__dirname, '../../static/sitemap.xml')

interface SitemapUrl {
  loc: string
  priority: number
  changefreq?: 'daily' | 'weekly' | 'monthly'
}

// Static routes with priorities
const STATIC_ROUTES: SitemapUrl[] = [
  { loc: '/', priority: 1.0, changefreq: 'weekly' },
  { loc: '/identity', priority: 0.9, changefreq: 'weekly' },
  { loc: '/ego', priority: 0.9, changefreq: 'weekly' },
  { loc: '/ego-gift', priority: 0.9, changefreq: 'weekly' },
  { loc: '/planner/md', priority: 0.8, changefreq: 'daily' },
  { loc: '/planner/md/gesellschaft', priority: 0.8, changefreq: 'daily' },
  { loc: '/planner/extraction', priority: 0.7, changefreq: 'monthly' },
  { loc: '/planner/deck', priority: 0.7, changefreq: 'monthly' },
]

function readJsonKeys(filename: string): string[] {
  const filepath = resolve(STATIC_DATA_DIR, filename)
  const data = JSON.parse(readFileSync(filepath, 'utf-8'))
  return Object.keys(data)
}

function generateSitemap(): void {
  const urls: SitemapUrl[] = [...STATIC_ROUTES]

  // Add identity detail pages
  const identityIds = readJsonKeys('identitySpecList.json')
  for (const id of identityIds) {
    urls.push({ loc: `/identity/${id}`, priority: 0.6, changefreq: 'monthly' })
  }

  // Add EGO detail pages
  const egoIds = readJsonKeys('egoSpecList.json')
  for (const id of egoIds) {
    urls.push({ loc: `/ego/${id}`, priority: 0.6, changefreq: 'monthly' })
  }

  // Add EGO Gift detail pages
  const egoGiftIds = readJsonKeys('egoGiftSpecList.json')
  for (const id of egoGiftIds) {
    urls.push({ loc: `/ego-gift/${id}`, priority: 0.5, changefreq: 'monthly' })
  }

  // Generate XML
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${urls
  .map(
    (url) => `  <url>
    <loc>${BASE_URL}${url.loc}</loc>
    <priority>${url.priority}</priority>${url.changefreq ? `\n    <changefreq>${url.changefreq}</changefreq>` : ''}
  </url>`
  )
  .join('\n')}
</urlset>
`

  writeFileSync(OUTPUT_PATH, xml)
  console.log(`Sitemap generated: ${OUTPUT_PATH}`)
  console.log(`  - Static routes: ${STATIC_ROUTES.length}`)
  console.log(`  - Identity pages: ${identityIds.length}`)
  console.log(`  - EGO pages: ${egoIds.length}`)
  console.log(`  - EGO Gift pages: ${egoGiftIds.length}`)
  console.log(`  - Total URLs: ${urls.length}`)
}

generateSitemap()
