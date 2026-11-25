import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { FileText, Loader2, Paperclip, X } from 'lucide-react'
import { PrivateFileCard, PrivateImage } from '../training/PrivateFile'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { formatBytes } from '../../utils/formatBytes'
import type { ContactFile } from '../../types'
import { CHAT_FILE_ACCEPT, CHAT_MAX_FILES } from './useStagedChatFiles'

const isImageType = (mimeType: string) => mimeType.startsWith('image/')

/**
 * The files of one chat message: pictures as private thumbnails (their space reserved from the
 * recorded size, so the chat does not jump as they load), PDFs as a download card. Each with the
 * date the yearly retention removes it, and a remove control where the viewer may delete.
 */
export function ChatMessageFiles({ files, onDelete }: { files: ContactFile[]; onDelete: (fileId: string) => void }) {
  const { t } = useTranslation('training')
  if (files.length === 0) return null

  return (
    <div className="mt-2 space-y-2">
      {files.map((file) => {
        const name = file.fileName === null ? null : decodeHtmlEntities(file.fileName)
        const meta = (
          <div className="flex items-center justify-between gap-3">
            <p className="text-[10px] text-surface-500">
              {t('comments.fileExpires', { date: format(new Date(file.expiresAt), 'dd.MM.yyyy') })}
            </p>
            {file.canDelete && (
              <button
                type="button"
                onClick={() => onDelete(file.id)}
                className="text-[11px] text-rose-300/80 hover:text-rose-200 underline underline-offset-2"
              >
                {t('comments.fileDelete')}
              </button>
            )}
          </div>
        )
        return (
          <div key={file.id} className="space-y-1">
            {isImageType(file.mimeType) ? (
              <PrivateImage url={file.url} alt={name ?? t('comments.fileAlt')} width={file.width} height={file.height} />
            ) : (
              <PrivateFileCard url={file.url} fileName={name} sizeBytes={file.sizeBytes} />
            )}
            {meta}
          </div>
        )
      })}
    </div>
  )
}

/** The attach button and the files picked for the next message, before it is sent. */
export function ChatAttachPicker({ staged, picking, error, onPick, onRemove, disabled }: {
  staged: File[]
  picking: boolean
  error: string | null
  onPick: (e: React.ChangeEvent<HTMLInputElement>) => void
  onRemove: (index: number) => void
  disabled?: boolean
}) {
  const { t } = useTranslation('training')
  const inputRef = useRef<HTMLInputElement>(null)
  const full = staged.length >= CHAT_MAX_FILES

  return (
    <div className="space-y-2">
      {staged.length > 0 && (
        <ul className="flex flex-wrap gap-2">
          {staged.map((file, index) => (
            <li
              key={`${file.name}-${index}`}
              className="inline-flex items-center gap-1.5 rounded-lg border border-surface-700 bg-surface-800 px-2 py-1 text-xs text-surface-200"
            >
              <FileText className="w-3.5 h-3.5 shrink-0 text-surface-400" aria-hidden="true" />
              <span className="max-w-40 truncate">{file.name}</span>
              <span className="text-surface-500">{formatBytes(file.size)}</span>
              <button
                type="button"
                onClick={() => onRemove(index)}
                className="p-0.5 rounded text-surface-400 hover:text-surface-100"
                aria-label={t('comments.fileRemove')}
              >
                <X className="w-3.5 h-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && <p role="alert" className="text-sm text-rose-400">{error}</p>}
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={CHAT_FILE_ACCEPT}
        onChange={onPick}
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled || picking || full}
        className="inline-flex items-center gap-1.5 text-xs text-surface-400 hover:text-surface-200 disabled:opacity-50"
      >
        {picking ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" /> : <Paperclip className="w-4 h-4" aria-hidden="true" />}
        {t('comments.fileAdd')}
      </button>
      <p className="text-[11px] text-surface-500">{t('comments.fileHint', { max: CHAT_MAX_FILES })}</p>
    </div>
  )
}
