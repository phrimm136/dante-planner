import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { act, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import sarasaLicence from '@/assets/fonts/ChineseFont.OFL.txt?url'
import corporateLogoLicence from '@/assets/fonts/Corporate-Logo-Bold-ver2.OFL.txt?url'
import { FONT_LICENCE_KINDS, FONT_LICENSES } from '@/lib/constants'
import i18n from '@/lib/i18n'
import { mountRoute } from '@/test-utils/mountRoute'
import LicensesPage from '../LicensesPage'

const TEST_DIR = path.dirname(fileURLToPath(import.meta.url))
const FONTS_DIR = path.resolve(TEST_DIR, '../../../assets/fonts')
const I18N_DIR = path.resolve(TEST_DIR, '../../../../../static/i18n')
const LANGS = ['EN', 'KR', 'JP', 'CN']
const FONT_FILE = /\.(ttf|otf|woff2?)$/i

const KOTRA_ATTRIBUTION_KR = "본 웹사이트에는 KOTRA에서 제작한 '코트라 볼드체'가 적용되어 있습니다."

function fontFiles(dir: string, prefix = ''): string[] {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const relative = `${prefix}${entry.name}`
    if (entry.isDirectory()) return fontFiles(path.join(dir, entry.name), `${relative}/`)
    return FONT_FILE.test(entry.name) ? [relative] : []
  })
}

function lookup(tree: unknown, key: string): unknown {
  return key
    .split('.')
    .reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], tree)
}

function renderPage() {
  mountRoute('/licenses', '/licenses', LicensesPage)
}

beforeEach(async () => {
  await i18n.changeLanguage('EN')
})

describe('LicensesPage', () => {
  it('renders one entry per listed font', async () => {
    renderPage()

    const entries = await screen.findAllByTestId('font-license')

    expect(entries.map((entry) => within(entry).getByRole('heading').textContent)).toEqual(
      FONT_LICENSES.map((font) => font.family),
    )
  })

  it('lists every font file under assets/fonts', () => {
    const covered = (file: string) =>
      FONT_LICENSES.some((font) =>
        font.files.some((listed) =>
          listed.endsWith('/') ? file.startsWith(listed) : file === listed,
        ),
      )

    const unlisted = fontFiles(FONTS_DIR).filter((file) => !covered(file))

    expect(unlisted).toEqual([])
  })

  it('names only paths that exist', () => {
    const listed = FONT_LICENSES.flatMap((font) => font.files)

    expect(listed.filter((file) => !fs.existsSync(path.join(FONTS_DIR, file)))).toEqual([])
  })

  it('shows the KOTRA attribution on the Korean page', async () => {
    renderPage()
    await act(async () => {
      await i18n.changeLanguage('KR')
    })

    expect(await screen.findByText(KOTRA_ATTRIBUTION_KR)).toBeInTheDocument()
  })

  it('ships the Sarasa and Corporate Logo licences next to their fonts and links them', async () => {
    expect(fs.existsSync(path.join(FONTS_DIR, 'ChineseFont.OFL.txt'))).toBe(true)
    expect(fs.existsSync(path.join(FONTS_DIR, 'Corporate-Logo-Bold-ver2.OFL.txt'))).toBe(true)

    renderPage()
    const hrefs = (await screen.findAllByRole('link', { name: 'License text' })).map((link) =>
      link.getAttribute('href'),
    )

    expect(hrefs).toEqual(expect.arrayContaining([sarasaLicence, corporateLogoLicence]))
    expect(hrefs).toHaveLength(FONT_LICENSES.filter((font) => font.licenceFileUrl).length)
  })

  it.each(LANGS)('carries every page key in %s', (lang) => {
    const common: unknown = JSON.parse(
      fs.readFileSync(path.join(I18N_DIR, lang, 'common.json'), 'utf-8'),
    )
    const keys = [
      'footer.links.licenses',
      'pages.licenses.title',
      'pages.licenses.intro',
      'pages.licenses.labels.author',
      'pages.licenses.labels.source',
      'pages.licenses.labels.licence',
      'pages.licenses.labels.licenceText',
      ...FONT_LICENCE_KINDS.map((kind) => `pages.licenses.licences.${kind}`),
      ...FONT_LICENSES.filter((font) => font.hasNote).map(
        (font) => `pages.licenses.notes.${font.id}`,
      ),
    ]

    expect(keys.filter((key) => typeof lookup(common, key) !== 'string')).toEqual([])
  })
})
