/**
 * Blob download helper.
 */

/**
 * Save a blob to the user's machine under the given filename.
 *
 * @example
 * downloadBlob('report.json', new Blob(['{}'], { type: 'application/json' }))
 */
export function downloadBlob(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
