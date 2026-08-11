import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FileText, ImageOff, Download } from 'lucide-react'
import clsx from 'clsx'
import { usePrivateFile } from '../../hooks/usePrivateFile'
import { useInView } from '../../hooks/useInView'
import { Modal } from '../ui/Modal'

interface PrivateImageProps {
  url: string
  alt: string
  /** Server-recorded dimensions. Used to reserve space so the thread does not jump while loading. */
  width?: number | null
  height?: number | null
  className?: string
  /** Extra content inside the lightbox — the delete control, when the viewer may delete. */
  lightboxExtra?: React.ReactNode
}

/**
 * A thumbnail of a private image, loaded only once it scrolls into view. A conversation from six
 * months ago should not pull down every photo in it the moment the training is opened.
 */
export function PrivateImage({ url, alt, width, height, className, lightboxExtra }: PrivateImageProps) {
  const { t } = useTranslation('training')
  const { ref, inView } = useInView<HTMLDivElement>({ rootMargin: '200px', threshold: 0 })
  const { objectUrl, isLoading, isError } = usePrivateFile(url, inView)
  const [open, setOpen] = useState(false)

  const ratio = width && height && height > 0 ? width / height : 4 / 3

  return (
    <>
      <div ref={ref} className={clsx('max-w-xs', className)}>
        {isError ? (
          <div
            className="flex flex-col items-center justify-center gap-1 p-4 rounded-lg border border-surface-700 bg-surface-800/60 text-surface-400 text-xs text-center"
            style={{ aspectRatio: ratio }}
            role="alert"
          >
            <ImageOff className="w-5 h-5" />
            {t('comments.fileUnavailable')}
          </div>
        ) : objectUrl ? (
          <button
            type="button"
            onClick={() => setOpen(true)}
            className="block w-full rounded-lg overflow-hidden border border-surface-800 hover:border-surface-600 transition-colors"
            aria-label={t('comments.filePreviewOpen')}
          >
            <img
              src={objectUrl}
              alt={alt}
              width={width ?? undefined}
              height={height ?? undefined}
              className="w-full h-auto"
            />
          </button>
        ) : (
          // Same box the image will occupy, so nothing below it shifts when the bytes arrive.
          <div
            className="rounded-lg border border-surface-800 bg-surface-800/60 animate-pulse"
            style={{ aspectRatio: ratio }}
            aria-hidden={!isLoading}
          />
        )}
      </div>

      <Modal isOpen={open} onClose={() => setOpen(false)} title={t('comments.filePreviewTitle')} size="xl">
        <div className="space-y-3">
          {objectUrl && (
            <img src={objectUrl} alt={alt} className="max-h-[70vh] w-full object-contain rounded-lg" />
          )}
          {lightboxExtra}
        </div>
      </Modal>
    </>
  )
}

interface PrivateFileCardProps {
  url: string
  /** Display name; the stored name is a bare UUID and tells the reader nothing. */
  fileName: string | null
  sizeBytes?: number | null
  className?: string
}

/**
 * A non-image private file (PDF). Downloads through the object URL rather than linking at the
 * endpoint: a plain `<a href>` would send no token and land on a 401.
 */
export function PrivateFileCard({ url, fileName, sizeBytes, className }: PrivateFileCardProps) {
  const { t } = useTranslation('training')
  const name = fileName ?? t('comments.fileFallbackName')

  return (
    <div
      className={clsx(
        'flex items-center gap-2.5 p-2.5 rounded-lg border border-surface-700 bg-surface-800/60',
        className,
      )}
    >
      <FileText className="w-4 h-4 shrink-0 text-rose-300" />
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-surface-100 truncate">{name}</span>
        {sizeBytes ? (
          <span className="block text-xs text-surface-500">{formatBytes(sizeBytes)}</span>
        ) : null}
      </span>
      <PrivateDownloadButton url={url} fileName={name} />
    </div>
  )
}

/**
 * Fetches on click, not on render. A materials list can hold dozens of PDFs, and rendering the
 * list is not a request to download all of them.
 */
export function PrivateDownloadButton({ url, fileName }: { url: string; fileName: string }) {
  const { t } = useTranslation('training')
  const [armed, setArmed] = useState(false)
  const { objectUrl, isLoading, isError } = usePrivateFile(url, armed)
  const saved = useRef(false)

  useEffect(() => {
    if (!objectUrl || saved.current) return
    saved.current = true
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = fileName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  }, [objectUrl, fileName])

  if (isError) {
    return (
      <span className="text-xs text-surface-400" role="alert">
        {t('comments.fileUnavailable')}
      </span>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setArmed(true)}
      disabled={isLoading}
      className="inline-flex items-center gap-1 px-2 py-1 text-xs rounded-md border border-surface-600 text-surface-200 hover:bg-surface-700 disabled:opacity-60 transition-colors"
    >
      <Download className="w-3.5 h-3.5" />
      {isLoading ? t('comments.fileDownloading') : t('comments.fileDownload')}
    </button>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
