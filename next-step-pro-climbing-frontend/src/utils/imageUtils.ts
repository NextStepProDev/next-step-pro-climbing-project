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

export function validateImageFile(file: File, allowedTypes?: string[]): string | null {
  if (file.size > MAX_INPUT_SIZE) {
    const sizeMB = (file.size / (1024 * 1024)).toFixed(1)
    return i18n.t('fileTooLarge', { ns: 'errors', size: sizeMB })
  }
  const allowed = allowedTypes ?? ['image/jpeg', 'image/png', 'image/webp']
  // iOS reports HEIC inconsistently and sometimes as an empty string, so fall back to the
  // extension rather than rejecting a photo the user clearly just picked from their camera roll.
  const type = file.type || typeFromExtension(file.name)
  if (!allowed.includes(type)) {
    return i18n.t('fileInvalidType', { ns: 'errors' })
  }
  return null
}

function typeFromExtension(name: string): string {
  const ext = name.toLowerCase().split('.').pop() ?? ''
  switch (ext) {
    case 'heic':
      return 'image/heic'
    case 'heif':
      return 'image/heif'
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg'
    case 'png':
      return 'image/png'
    case 'webp':
      return 'image/webp'
    case 'pdf':
      return 'application/pdf'
    default:
      return ''
  }
}

/**
 * Types the comment-attachment picker offers. HEIC/HEIF are accepted at the picker and converted
 * in the browser — they never reach the server, which has no HEIC decoder (nor does any JVM
 * without a native library).
 */
export const ATTACHMENT_INPUT_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
  'application/pdf',
]

export const isImageInput = (file: File) =>
  (file.type || typeFromExtension(file.name)).startsWith('image/')

/**
 * Converts any picked image to a JPEG the server will accept, always re-encoding.
 *
 * <p>Always, because {@link compressImage} returns the original untouched when it is already small
 * — and a small HEIC returned untouched is exactly the file the server cannot read. JPEG rather
 * than WebP because the backend has no WebP decoder either, so a WebP could be neither stripped of
 * its metadata nor measured for the dimensions the thread uses to reserve space.
 *
 * <p>⚠️ HEIC decodes in Safari (macOS and iOS) and not in desktop Chrome or Firefox, which ship no
 * HEIC decoder. In practice HEIC arrives from an iPhone, which is WebKit — but the failure has to
 * say what to do about it, hence the specific error rather than "could not read file".
 */
export async function toUploadableImage(file: File): Promise<File> {
  const bitmap = await loadImage(file)

  let { width, height } = bitmap
  if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
    const ratio = Math.min(MAX_DIMENSION / width, MAX_DIMENSION / height)
    width = Math.round(width * ratio)
    height = Math.round(height * ratio)
  }

  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')!
  // JPEG has no alpha; without this a transparent PNG comes out on a black background.
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, width, height)
  ctx.drawImage(bitmap.image, 0, 0, width, height)

  const blob = await new Promise<Blob | null>(resolve =>
    canvas.toBlob(resolve, 'image/jpeg', ATTACHMENT_QUALITY),
  )
  if (!blob) {
    throw new Error(i18n.t('imageLoadFailed', { ns: 'errors' }))
  }
  const name = file.name.replace(/\.[^.]+$/, '') + '.jpg'
  return new File([blob], name, { type: 'image/jpeg' })
}

/** Higher than the CMS default: screenshots of a watch put fine digits under the compressor. */
const ATTACHMENT_QUALITY = 0.9

function loadImage(file: File): Promise<{ image: HTMLImageElement; width: number; height: number }> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      URL.revokeObjectURL(img.src)
      resolve({ image: img, width: img.naturalWidth, height: img.naturalHeight })
    }
    img.onerror = () => {
      URL.revokeObjectURL(img.src)
      reject(new Error(i18n.t('imageUnreadableHeic', { ns: 'errors' })))
    }
    img.src = URL.createObjectURL(file)
  })
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
