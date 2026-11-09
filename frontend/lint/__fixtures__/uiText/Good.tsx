declare function t(key: string): string

export function Good({ a, b }: { a: string; b: string }) {
  const warning = { title: 'pages.plannerMD.save.syncOffWarning.title' }
  return (
    <div>
      <svg>
        <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515" />
      </svg>
      <p className="text-sm font-medium">{t('common:close')}</p>
      <img alt="" />
      <span>
        {a} / {b}
      </span>
      <span>日本語</span>
      <input placeholder="https://example.com" />
      <section aria-labelledby="dialogTitle" aria-describedby="dialogBody" />
      <span>{t(warning.title)}</span>
    </div>
  )
}
