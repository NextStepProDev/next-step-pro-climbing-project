import { useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ATTACHMENT_INPUT_TYPES, isImageInput, toUploadableImage, validateImageFile } from '../../utils/imageUtils'
import { getErrorMessage } from '../../utils/errors'

/** Mirrors ContactMessageFile.MAX_PER_MESSAGE on the backend. */
export const CHAT_MAX_FILES = 3

/** What the file input offers: pictures (HEIC included, converted here) and PDFs. */
export const CHAT_FILE_ACCEPT = 'image/*,.heic,.heif,application/pdf'

/**
 * The files picked for the next chat message, prepared the way the training threads prepare
 * theirs: pictures are shrunk and re-encoded in the browser, which turns an iPhone HEIC into a JPEG
 * the server can read and drops EXIF (with the location) before anything leaves the device.
 */
export function useStagedChatFiles() {
  const { t } = useTranslation('training')
  const [staged, setStaged] = useState<File[]>([])
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const pick = async (e: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(e.target.files ?? [])
    // Reset at once, so picking the same file twice in a row still fires a change event.
    e.target.value = ''
    if (picked.length === 0) return
    if (staged.length + picked.length > CHAT_MAX_FILES) {
      setError(t('comments.fileTooMany', { max: CHAT_MAX_FILES }))
      return
    }
    setError(null)
    setPicking(true)
    try {
      const prepared: File[] = []
      for (const file of picked) {
        const invalid = validateImageFile(file, ATTACHMENT_INPUT_TYPES)
        if (invalid) {
          setError(invalid)
          return
        }
        prepared.push(isImageInput(file) ? await toUploadableImage(file) : file)
      }
      setStaged((current) => [...current, ...prepared])
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setPicking(false)
    }
  }

  const remove = (index: number) => setStaged((current) => current.filter((_, i) => i !== index))
  const clear = () => {
    setStaged([])
    setError(null)
  }

  return { staged, picking, error, pick, remove, clear }
}
