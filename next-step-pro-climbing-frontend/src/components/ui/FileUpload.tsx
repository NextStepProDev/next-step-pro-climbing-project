import { useState, useRef, type DragEvent, type ChangeEvent, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Upload, X, Image as ImageIcon, Loader2 } from 'lucide-react'
import clsx from 'clsx'
import { Button } from './Button'
import { compressImage, MAX_INPUT_SIZE_MB } from '../../utils/imageUtils'

interface FileUploadProps {
  onFileSelect: (files: File[]) => void
  onClear?: () => void
  accept?: string
  maxSizeMB?: number
  multiple?: boolean
  previews?: string[]
}

export function FileUpload({
  onFileSelect,
  onClear,
  accept = 'image/jpeg,image/png,image/webp',
  maxSizeMB = MAX_INPUT_SIZE_MB,
  multiple = false,
  previews = [],
}: FileUploadProps) {
  const { t } = useTranslation('admin')
  const [dragActive, setDragActive] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [compressing, setCompressing] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const validateFile = (file: File): boolean => {
    // Check file type
    const acceptedTypes = accept.split(',').map((t) => t.trim())
    if (!acceptedTypes.includes(file.type)) {
      return false
    }

    // Check file size
    const maxBytes = maxSizeMB * 1024 * 1024
    if (file.size > maxBytes) {
      return false
    }

    return true
  }

  const handleFiles = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return

    const files = Array.from(fileList)
    const validFiles: File[] = []
    const errors: string[] = []

    files.forEach((file) => {
      if (validateFile(file)) {
        validFiles.push(file)
      } else {
        errors.push(t('editorUi.fileUpload.invalid', { name: file.name }))
      }
    })

    if (errors.length > 0) {
      setError(errors.join(', '))
    } else {
      setError(null)
    }

    if (validFiles.length > 0) {
      setCompressing(true)
      try {
        const compressed = await Promise.all(validFiles.map(compressImage))
        onFileSelect(compressed)
      } catch {
        // Without this the rejection went nowhere: the spinner stopped and nothing was selected,
        // with no word on screen about why.
        setError(t('editorUi.fileUpload.optimizeFailed'))
      } finally {
        setCompressing(false)
      }
    }
  }

  const handleDrag = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true)
    } else if (e.type === 'dragleave') {
      setDragActive(false)
    }
  }

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    setDragActive(false)

    if (e.dataTransfer.files) {
      void handleFiles(e.dataTransfer.files)
    }
  }

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      void handleFiles(e.target.files)
    }
    // A refused file (wrong type, too big) left its name in the input, so picking the same file
    // again after fixing it fired no change at all. handleFiles copies the list synchronously first.
    e.target.value = ''
  }

  const handleClick = () => {
    fileInputRef.current?.click()
  }

  // The drop zone is the only way to the hidden file input, so it has to work from the keyboard too.
  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      handleClick()
    }
  }

  const handleClear = () => {
    setError(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
    onClear?.()
  }

  return (
    <div className="w-full">
      {previews.length > 0 ? (
        <div className="space-y-3">
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {previews.map((preview, index) => (
              <div key={index} className="relative">
                <img
                  src={preview}
                  alt={t('editorUi.fileUpload.preview', { n: index + 1 })}
                  className="w-full h-32 object-cover rounded-lg border border-surface-700"
                />
              </div>
            ))}
          </div>
          <Button
            type="button"
            variant="danger"
            size="sm"
            onClick={handleClear}
          >
            <X className="h-4 w-4 mr-1" />
            {t('editorUi.fileUpload.clearAll')}
          </Button>
        </div>
      ) : (
        <div
          className={clsx(
            'border-2 border-dashed rounded-lg p-8 text-center cursor-pointer transition-colors',
            dragActive
              ? 'border-primary-500 bg-primary-500/5'
              : 'border-surface-700 hover:border-surface-600 hover:bg-surface-800/50',
            error && 'border-rose-500/50'
          )}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
          onClick={handleClick}
          onKeyDown={handleKeyDown}
          role="button"
          tabIndex={0}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept={accept}
            multiple={multiple}
            onChange={handleChange}
            className="hidden"
          />

          <div className="flex flex-col items-center gap-2">
            {compressing ? (
              <Loader2 className="h-12 w-12 text-primary-500 animate-spin" />
            ) : dragActive ? (
              <Upload className="h-12 w-12 text-primary-500" />
            ) : (
              <ImageIcon className="h-12 w-12 text-surface-400" />
            )}
            <div className="text-surface-300">
              {compressing ? (
                <span className="font-medium text-primary-400">{t('editorUi.fileUpload.optimizing')}</span>
              ) : (
                <>
                  <span className="font-medium text-surface-100">
                    {t(multiple ? 'editorUi.fileUpload.chooseMany' : 'editorUi.fileUpload.chooseOne')}
                  </span>
                  {' '}{t('editorUi.fileUpload.orDrop')}
                </>
              )}
            </div>
            <div className="text-sm text-surface-400">
              {accept.split(',').map((type) => type.split('/')[1].toUpperCase()).join(', ')}
              {' '}
              {t(multiple ? 'editorUi.fileUpload.maxEach' : 'editorUi.fileUpload.maxOne', { mb: maxSizeMB })}
            </div>
          </div>
        </div>
      )}

      {error && (
        <div className="mt-2 text-sm text-rose-400">
          {error}
        </div>
      )}
    </div>
  )
}
