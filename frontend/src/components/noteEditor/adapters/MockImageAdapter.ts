import type { IImageUploadAdapter } from './IImageUploadAdapter'
import { validateUploadFile, type UploadOptions } from './IImageUploadAdapter'

/**
 * Mock Image Upload Adapter
 *
 * Uses browser blob URLs for development/testing.
 * Tracks all created blob URLs for proper cleanup on unmount.
 *
 * Note: Blob URLs are only valid in the current browser session.
 * They cannot be persisted and will become invalid after page refresh.
 */
export class MockImageAdapter implements IImageUploadAdapter {
  /** Set of blob URLs created by this adapter instance */
  private blobUrls: Set<string> = new Set()

  /** Upload options */
  private options: UploadOptions

  constructor(options: UploadOptions = {}) {
    this.options = options
  }

  /**
   * Create a blob URL for the file
   * Simulates upload with a small delay
   */
  async upload(file: File): Promise<string> {
    // Validate file
    const validation = validateUploadFile(file, this.options)
    if (!validation.valid) {
      throw new Error(validation.error)
    }

    // Simulate network delay for realistic behavior
    await new Promise((resolve) => setTimeout(resolve, 100))

    // Create blob URL
    const url = URL.createObjectURL(file)
    this.blobUrls.add(url)

    return url
  }

  /**
   * Revoke a blob URL
   */
  async delete(url: string): Promise<void> {
    if (this.blobUrls.has(url)) {
      URL.revokeObjectURL(url)
      this.blobUrls.delete(url)
    }
  }

  /**
   * Revoke all blob URLs
   * Call this when the editor unmounts
   */
  cleanup(): void {
    for (const url of this.blobUrls) {
      URL.revokeObjectURL(url)
    }
    this.blobUrls.clear()
  }

  /**
   * Get the count of tracked blob URLs (for debugging)
   */
  get trackedCount(): number {
    return this.blobUrls.size
  }
}

/**
 * Create a mock image adapter instance
 * Factory function for consistent instantiation
 */
export function createMockImageAdapter(options?: UploadOptions): IImageUploadAdapter {
  return new MockImageAdapter(options)
}
