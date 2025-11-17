import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Multiply an image with a color pixel-wise using Canvas API
 * @param imageUrl - URL of the image to multiply
 * @param hexColor - Hex color to multiply with (e.g., '#fe0000')
 * @returns Promise that resolves to data URL of the multiplied image
 */
export async function multiplyImageColor(imageUrl: string, hexColor: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'

    img.onload = () => {
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')

      if (!ctx) {
        reject(new Error('Could not get canvas context'))
        return
      }

      canvas.width = img.width
      canvas.height = img.height

      // Draw the original image
      ctx.drawImage(img, 0, 0)

      // Get pixel data
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height)
      const data = imageData.data

      // Parse hex color to RGB
      const color = hexColor.replace('#', '')
      const r = parseInt(color.substring(0, 2), 16)
      const g = parseInt(color.substring(2, 4), 16)
      const b = parseInt(color.substring(4, 6), 16)

      // Multiply each pixel
      for (let i = 0; i < data.length; i += 4) {
        data[i] = (data[i] * r) / 255       // Red
        data[i + 1] = (data[i + 1] * g) / 255 // Green
        data[i + 2] = (data[i + 2] * b) / 255 // Blue
        // data[i + 3] is alpha - preserve it
      }

      // Put the modified pixel data back
      ctx.putImageData(imageData, 0, 0)

      // Convert to data URL
      resolve(canvas.toDataURL())
    }

    img.onerror = () => {
      reject(new Error(`Failed to load image: ${imageUrl}`))
    }

    img.src = imageUrl
  })
}
