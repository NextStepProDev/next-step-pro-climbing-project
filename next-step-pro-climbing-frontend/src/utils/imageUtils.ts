import i18n from '../i18n'

// Upper bound on the *input* file the admin may pick. Deliberately generous: any phone/camera
// JPEG/PNG fits, and compressImage() downscales+re-encodes it to a small WebP before upload, so
// the server never receives the big original. The cap only guards the browser against decoding an
// absurdly large file (canvas RAM ≈ width·height·4 bytes). Not the stored size — that ends up tiny.
export const MAX_INPUT_SIZE_MB = 50
const MAX_INPUT_SIZE = MAX_INPUT_SIZE_MB * 1024 * 1024
const MAX_DIMENSION = 1920
const OUTPUT_QUALITY = 0.85
const COMPRESS_THRESHOLD = 2 * 1024 * 1024 // 2 MB — below this AND within dimension, leave untouched

export function validateImageFile(file: File): string | null {
  if (file.size > MAX_INPUT_SIZE) {
    const sizeMB = (file.size / (1024 * 1024)).toFixed(1)
    return i18n.t('fileTooLarge', { ns: 'errors', size: sizeMB })
  }
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    return i18n.t('fileInvalidType', { ns: 'errors' })
  }
  return null
}

let webpEncodeSupported: boolean | null = null

/** Whether canvas can encode WebP (Safari <14 cannot; falls back to JPEG). Cached after first probe. */
function supportsWebpEncoding(): boolean {
  if (webpEncodeSupported !== null) return webpEncodeSupported
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 1
    canvas.height = 1
    webpEncodeSupported = canvas.toDataURL('image/webp').startsWith('data:image/webp')
  } catch {
    webpEncodeSupported = false
  }
  return webpEncodeSupported
}

/**
 * Downscale + re-encode an image on the client before upload. Large originals are capped at
 * {@link MAX_DIMENSION} px on the longest side and re-encoded to WebP (JPEG fallback), which is
 * why a normal large camera/phone photo can be picked and still uploads as a small file — the
 * heavy work runs on the admin's machine, keeping the resource-constrained server untouched.
 * WebP preserves PNG transparency, so logos/badges with alpha stay correct.
 */
export async function compressImage(file: File): Promise<File> {
  if (file.size <= COMPRESS_THRESHOLD && !(await exceedsDimension(file))) {
    return file
  }

  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)

      let { width, height } = img
      if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
        const ratio = Math.min(MAX_DIMENSION / width, MAX_DIMENSION / height)
        width = Math.round(width * ratio)
        height = Math.round(height * ratio)
      }

      const canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')!
      ctx.drawImage(img, 0, 0, width, height)

      const mimeType = supportsWebpEncoding() ? 'image/webp' : 'image/jpeg'
      const ext = mimeType === 'image/webp' ? '.webp' : '.jpg'
      canvas.toBlob(
        blob => {
          // Fall back to the original if encoding failed or somehow produced a larger file
          // (e.g. an already-optimized small image tripped the threshold via dimension only).
          if (!blob || blob.size >= file.size) {
            resolve(file)
            return
          }
          const name = file.name.replace(/\.[^.]+$/, ext)
          resolve(new File([blob], name, { type: mimeType }))
        },
        mimeType,
        OUTPUT_QUALITY,
      )
    }
    img.onerror = () => reject(new Error(i18n.t('imageLoadFailed', { ns: 'errors' })))
    img.src = URL.createObjectURL(file)
  })
}

function exceedsDimension(file: File): Promise<boolean> {
  return new Promise(resolve => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)
      resolve(img.width > MAX_DIMENSION || img.height > MAX_DIMENSION)
    }
    img.onerror = () => resolve(false)
    img.src = URL.createObjectURL(file)
  })
}
