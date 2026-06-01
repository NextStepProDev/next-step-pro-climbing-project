import i18n from '../i18n'

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
const MAX_DIMENSION = 1920
const OUTPUT_QUALITY = 0.85
const COMPRESS_THRESHOLD = 2 * 1024 * 1024 // 2 MB

export function validateImageFile(file: File): string | null {
  if (file.size > MAX_FILE_SIZE) {
    const sizeMB = (file.size / (1024 * 1024)).toFixed(1)
    return i18n.t('fileTooLarge', { ns: 'errors', size: sizeMB })
  }
  const allowed = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowed.includes(file.type)) {
    return i18n.t('fileInvalidType', { ns: 'errors' })
  }
  return null
}

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

      const mimeType = file.type === 'image/png' ? 'image/png' : 'image/jpeg'
      canvas.toBlob(
        blob => {
          if (!blob) {
            resolve(file)
            return
          }
          const ext = mimeType === 'image/png' ? '.png' : '.jpg'
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
