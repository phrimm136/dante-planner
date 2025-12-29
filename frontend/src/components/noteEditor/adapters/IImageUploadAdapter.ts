/**
 * Image Upload Adapter Interface
 *
 * Defines the contract for image upload implementations.
 * This abstraction allows swapping between:
 * - MockImageAdapter: Uses blob URLs for development
 * - CloudflareImageAdapter: Real upload to Cloudflare (future)
 */

/**
 * Image upload adapter interface
 */
export interface IImageUploadAdapter {
  /**
   * Upload a file and return the accessible URL
   * @param file - The file to upload
   * @returns Promise resolving to the image URL
   */
  upload(file: File): Promise<string>

  /**
   * Delete an uploaded image
   * @param url - The URL of the image to delete
   */
  delete(url: string): Promise<void>

  /**
   * Clean up all tracked resources
   * Should be called when the editor unmounts
   */
  cleanup(): void
}

/**
 * Upload result with metadata
 */
export interface UploadResult {
  /** URL to access the uploaded image */
  url: string
  /** Original filename */
  filename: string
  /** File size in bytes */
  size: number
  /** MIME type */
  mimeType: string
}

/**
 * Upload options for customization
 */
export interface UploadOptions {
  /** Maximum file size in bytes (default: 5MB) */
  maxSize?: number
  /** Allowed MIME types (default: image/*) */
  allowedTypes?: string[]
}

/**
 * Default upload options
 */
export const DEFAULT_UPLOAD_OPTIONS: Required<UploadOptions> = {
  maxSize: 5 * 1024 * 1024, // 5MB
  allowedTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
}

/**
 * Validate a file against upload options
 */
export function validateUploadFile(
  file: File,
  options: UploadOptions = {}
): { valid: boolean; error?: string } {
  const opts = { ...DEFAULT_UPLOAD_OPTIONS, ...options }

  if (file.size > opts.maxSize) {
    const maxMB = opts.maxSize / (1024 * 1024)
    return { valid: false, error: `File size exceeds ${maxMB}MB limit` }
  }

  if (!opts.allowedTypes.includes(file.type)) {
    return { valid: false, error: `File type ${file.type} is not allowed` }
  }

  return { valid: true }
}
