import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FileText, Image as ImageIcon, Link2, Loader2, Plus, Upload, X } from 'lucide-react'
import { getErrorMessage } from '../../utils/errors'
import type { AttachmentInput } from '../../types'

const MAX_ATTACHMENTS = 3
const ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp'

interface AttachmentEditorProps {
  value: AttachmentInput[]
  onChange: (next: AttachmentInput[]) => void
  // Uploads a picked file and returns its stored metadata (athlete vs coach endpoint)
  onUpload: (file: File) => Promise<{ filename: string; originalName: string; mimeType: string; sizeBytes: number }>
}

/**
 * Up to 3 materials per training: pasted links (label + URL) and/or uploaded files (PDF/image).
 * Files are uploaded immediately and referenced by their stored filename on save. URL validity
 * is checked on submit in the parent.
 */
export function AttachmentEditor({ value, onChange, onUpload }: AttachmentEditorProps) {
  const { t } = useTranslation('training')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState<string | null>(null)

  const update = (index: number, patch: Partial<AttachmentInput>) => {
    onChange(value.map((a, i) => (i === index ? { ...a, ...patch } : a)))
  }
  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))
  const addLink = () => onChange([...value, { kind: 'LINK', url: '', label: '' }])

  const pickFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = '' // allow re-picking the same file
    if (!file) return
    setUploadError(null)
    setUploading(true)
    try {
      const up = await onUpload(file)
      onChange([...value, {
        kind: 'FILE',
        filename: up.filename,
        originalName: up.originalName,
        mimeType: up.mimeType,
        sizeBytes: up.sizeBytes,
        label: '',
      }])
    } catch (err) {
      setUploadError(getErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div>
      <label className="block text-sm text-surface-400 mb-1">{t('form.attachments')}</label>
      <p className="text-xs text-surface-500 mb-2">{t('form.attachmentsHint')}</p>

      <div className="space-y-2">
        {value.map((att, i) => (
          <div key={i} className="flex gap-2 items-start">
            <div className="flex-1 space-y-1.5">
              <input
                type="text"
                value={att.label ?? ''}
                onChange={(e) => update(i, { label: e.target.value })}
                maxLength={120}
                placeholder={t('form.attachmentLabelPlaceholder')}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-1.5 text-sm text-surface-100"
              />
              {att.kind === 'FILE' ? (
                <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-800/60 border border-surface-700 text-sm text-surface-300">
                  {att.mimeType === 'application/pdf'
                    ? <FileText className="w-3.5 h-3.5 shrink-0 text-rose-300" />
                    : <ImageIcon className="w-3.5 h-3.5 shrink-0 text-primary-300" />}
                  <span className="truncate">{att.originalName ?? att.filename}</span>
                </div>
              ) : (
                <div className="relative">
                  <Link2 className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-surface-500" />
                  <input
                    type="url"
                    inputMode="url"
                    value={att.url ?? ''}
                    onChange={(e) => update(i, { url: e.target.value })}
                    maxLength={2048}
                    placeholder={t('form.attachmentUrlPlaceholder')}
                    className="w-full bg-surface-800 border border-surface-700 rounded-lg pl-8 pr-3 py-1.5 text-sm text-surface-100"
                  />
                </div>
              )}
            </div>
            <button
              type="button"
              onClick={() => remove(i)}
              className="p-1.5 mt-0.5 rounded-lg text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
              title={t('form.attachmentRemove')}
              aria-label={t('form.attachmentRemove')}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>

      {uploadError && <p className="text-sm text-rose-400/80 mt-2">{uploadError}</p>}

      {value.length < MAX_ATTACHMENTS && (
        <div className="mt-2 flex flex-wrap gap-4">
          <button
            type="button"
            onClick={addLink}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('form.attachmentAdd')}
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors disabled:opacity-60"
          >
            {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
            {t('form.attachmentUpload')}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept={ACCEPT}
            onChange={pickFile}
            className="hidden"
          />
        </div>
      )}
    </div>
  )
}
