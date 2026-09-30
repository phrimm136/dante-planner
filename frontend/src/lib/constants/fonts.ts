import bebasNeueLicence from '@/assets/fonts/Bebas_Neue/OFL.txt?url'
import sarasaLicence from '@/assets/fonts/ChineseFont.OFL.txt?url'
import corporateLogoLicence from '@/assets/fonts/Corporate-Logo-Bold-ver2.OFL.txt?url'
import plannerLatinLicence from '@/assets/fonts/planner-latin.OFL.txt?url'
import plannerNumeralsLicence from '@/assets/fonts/planner-numerals.OFL.txt?url'
import pretendardLicence from '@/assets/fonts/Pretendard-1.3.9/LICENSE.txt?url'
import sCoreDreamLicence from '@/assets/fonts/S-Core_Dream_OTF/S-Core_Dream_License.png?url'

export const FONT_LICENCE_KINDS = ['ofl', 'sCore', 'kogl1', 'embedded', 'dafontFree'] as const

export type FontLicenceKind = (typeof FONT_LICENCE_KINDS)[number]

export interface FontLicense {
  id: string
  family: string
  author: string
  sourceUrl?: string
  licence: FontLicenceKind
  licenceUrl?: string
  licenceFileUrl?: string
  copyright?: readonly string[]
  quote?: string
  hasNote: boolean
  files: readonly string[]
}

const OFL_URL = 'https://openfontlicense.org'

export const FONT_LICENSES: readonly FontLicense[] = [
  {
    id: 'plannerLatin',
    family: 'PlannerLatin (Mikodacs)',
    author: 'gluk',
    sourceUrl: 'https://www.glukfonts.pl',
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: plannerLatinLicence,
    copyright: [
      'Copyright (c) 2012-2016, gluk (www.glukfonts.pl | gluksza@wp.pl), with Reserved Font Name Mikodacs.',
    ],
    hasNote: true,
    files: ['planner-latin.otf'],
  },
  {
    id: 'plannerNumerals',
    family: 'PlannerNumerals',
    author: "Dante's Planner contributors",
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: plannerNumeralsLicence,
    copyright: ["Copyright 2026 Dante's Planner contributors"],
    hasNote: true,
    files: ['planner-numerals.ttf'],
  },
  {
    id: 'pretendard',
    family: 'Pretendard',
    author: 'Kil Hyung-jin',
    sourceUrl: 'https://github.com/orioncactus/pretendard',
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: pretendardLicence,
    copyright: [
      'Copyright (c) 2021, Kil Hyung-jin (https://github.com/orioncactus/pretendard), with Reserved Font Name Pretendard.',
    ],
    hasNote: false,
    files: ['Pretendard-1.3.9/'],
  },
  {
    id: 'sarasaGothic',
    family: 'Sarasa Gothic SC',
    author: 'Belleve Invis',
    sourceUrl: 'https://github.com/be5invis/Sarasa-Gothic',
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: sarasaLicence,
    copyright: [
      'Copyright (c) 2015-2025, Renzhi Li (aka. Belleve Invis, belleve@typeof.net).',
      'Portions Copyright (c) 2016 The Inter Project Authors.',
      "Portions Copyright (c) 2014-2021 Adobe Systems Incorporated (http://www.adobe.com/), with Reserved Font Name 'Source'.",
      'Portions Copyright (c) 2012 Google Inc.',
    ],
    hasNote: false,
    files: ['ChineseFont.ttf'],
  },
  {
    id: 'corporateLogo',
    family: 'Corporate Logo Bold ver2',
    author: 'LOGOTYPE.JP',
    sourceUrl: 'https://logotype.jp/corporate-logo-font-dl.html',
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: corporateLogoLicence,
    copyright: [
      "[KANA_main] LOGOTYPE.JP [Source Han Sans] © 2014-2020 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.",
    ],
    hasNote: true,
    files: ['Corporate-Logo-Bold-ver2.ttf'],
  },
  {
    id: 'higashiOmeGothic',
    family: 'HigashiOme Gothic C',
    author: 'Zorsuho',
    licence: 'embedded',
    quote:
      'This font is a free software. Unlimited permission is granted to use, copy, and distribute it, with or without modification, either commercially and noncommercially.',
    hasNote: false,
    files: ['HigashiOme-Gothic-C(2)/'],
  },
  {
    id: 'kotraBold',
    family: 'KOTRA Bold',
    author: 'KOTRA',
    sourceUrl: 'https://www.kotra.or.kr/subList/20000005965?tabid=20',
    licence: 'kogl1',
    licenceUrl: 'https://www.kogl.or.kr/info/licenseType1.do',
    hasNote: true,
    files: ['KOTRA_BOLD.ttf'],
  },
  {
    id: 'sCoreDream',
    family: 'S-Core Dream',
    author: 'S-Core',
    sourceUrl: 'https://s-core.co.kr/company/font/',
    licence: 'sCore',
    licenceUrl: 'https://s-core.co.kr/company/font/',
    licenceFileUrl: sCoreDreamLicence,
    quote: [
      '에스코어 드림 라이선스',
      '1. S-Core 홈페이지를 통해 배포되는 에스코어 드림의 지적 재산권은 S-Core에 있습니다.',
      '2. 에스코어 드림은 개인 및 기업 사용자를 포함한 모든 사용자에게 무료로 제공되며 자유롭게 재배포 하실 수 있습니다.',
      '3. 에스코어 드림은 어떠한 이유로도 지적 재산권 이외의 사용자가 폰트 파일을 수정, 판매할 수 없으며 배포되는 파일 형태 그대로 사용해야 합니다.',
      '4. 에스코어 드림은 본 저작권 안내를 포함해서 다른 소프트웨어와 번들하거나 재배포 또는 판매가 가능합니다. 에스코어 드림 저작권 안내를 포함하기 어려울 경우, 에스코어 드림의 출처 표기를 권장합니다.',
      '예) 이 페이지에는 S-Core에서 제공한 에스코어 드림이 적용되어 있습니다.',
      '5. 에스코어 드림을 사용한 인쇄물, 광고물(온라인 포함)의 이미지는 S-Core의 프로모션을 위해 활용될 수 있습니다. 이를 원치 않을 경우, 언제든지 당사에 요청하실 수 있습니다.',
    ].join('\n'),
    hasNote: true,
    files: ['S-Core_Dream_OTF/'],
  },
  {
    id: 'bebasNeue',
    family: 'Bebas Neue',
    author: 'Dharma Type',
    sourceUrl: 'https://github.com/dharmatype/Bebas-Neue',
    licence: 'ofl',
    licenceUrl: OFL_URL,
    licenceFileUrl: bebasNeueLicence,
    copyright: ['Copyright © 2010 by Dharma Type.'],
    hasNote: false,
    files: ['Bebas_Neue/'],
  },
  {
    id: 'tagmarker',
    family: 'Tagmarker',
    author: 'Pop Ovidiu Sebastian',
    sourceUrl: 'https://www.dafont.com/tagmarker.font',
    licence: 'dafontFree',
    licenceUrl: 'https://www.dafont.com/tagmarker.font',
    hasNote: false,
    files: ['tagmarker.ttf'],
  },
]
