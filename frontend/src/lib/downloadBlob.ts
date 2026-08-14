/**
 * Blob download helper.
 */

/**
 * Save a blob to the user's machine under the given filename.
 *
 * Answers whether the download was handed to the browser, so a caller does not
 * announce a saved file on the strength of having called this.
 *
 * @example
 * downloadBlob('report.json', new Blob(['{}'], { type: 'application/json' }))
 */
export function downloadBlob(filename: string, blob: Blob): boolean {
  if (blob.size === 0) return false

  let url: string | null = null
  try {
    url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    return true
  } catch (error) {
    console.error('Download failed:', error)
    return false
  } finally {
    if (url !== null) URL.revokeObjectURL(url)
  }
}
