export function downloadBlob(filename: string, blob: Blob): boolean {
  if (blob.size === 0) return false

  let url: string | null = null
  let link: HTMLAnchorElement | null = null
  try {
    url = URL.createObjectURL(blob)
    link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    return true
  } catch (error) {
    console.error('Download failed:', error)
    return false
  } finally {
    if (link?.parentNode === document.body) document.body.removeChild(link)
    if (url !== null) URL.revokeObjectURL(url)
  }
}
