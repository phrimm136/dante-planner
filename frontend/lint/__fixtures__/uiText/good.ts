export function uploadedUrl(url: string | undefined): string {
  if (!url) throw new Error('Upload failed: No URL returned')
  return url
}

export const d = 'M20 2C20 1.44772 19.5523 1 19 1'

export const rel = 'noopener noreferrer'

export const toastClassNames = { title: 'text-sm font-medium' }
