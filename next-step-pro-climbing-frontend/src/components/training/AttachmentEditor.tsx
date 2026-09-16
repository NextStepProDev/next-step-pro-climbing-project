import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FileText, Image as ImageIcon, Link2, Loader2, Plus, Upload, X } from 'lucide-react'
import { getErrorMessage } from '../../utils/errors'
import type { AttachmentInput } from '../../types'

/** Mirrors TrainingAttachment.MAX_PER_TRAINING on the backend. */
const MAX_ATTACHMENTS = 6
const ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp'

interface AttachmentEditorProps {
  value: AttachmentInput[]
  onChange: (next: AttachmentInput[]) => void
  // Uploads a picked file and returns its stored metadata (athlete vs coach endpoint)
  onUpload: (file: File) => Promise<{ filename: string; originalName: string; mimeType: string; sizeBytes: number }>
}

/**
 * Up to MAX_ATTACHMENTS materials per training: pasted links (label + URL) and/or uploaded files
 * (PDF/image). Several files can be picked at once; each is uploaded immediately and referenced by
 * its stored filename on save. URL validity is checked on submit in the parent.
 */
export function AttachmentEditor({ value, onChange, onUpload }: AttachmentEditorProps) {
  const { t } = useTranslation('training')
  const fileInputRef = useRef<HTMLInputElement>(null)
  // null while idle; counts files, not bytes — six photos are a dozen silent seconds otherwise.
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const uploading = progress !== null

  // Six files take seconds, and the rows already on screen stay live meanwhile — a material removed
  // or a label retyped mid-upload must survive the merge below, which would otherwise write back
  // the list as it looked when the picker closed.
  const valueRef = useRef(value)
  useEffect(() => { valueRef.current = value }, [value])

  const update = (index: number, patch: Partial<AttachmentInput>) => {
    onChange(value.map((a, i) => (i === index ? { ...a, ...patch } : a)))
  }
  // Both change the free-slot count, and a refusal message names it ("free slots: 1") — so the
  // message stops being true the moment either runs.
  const remove = (index: number) => {
    setUploadError(null)
    onChange(value.filter((_, i) => i !== index))
  }
  const addLink = () => {
    setUploadError(null)
    onChange([...value, { kind: 'LINK', url: '', label: '' }])
  }

  const pickFiles = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(e.target.files ?? [])
    e.target.value = '' // allow re-picking the same file
    if (picked.length === 0) return

    const free = MAX_ATTACHMENTS - value.length
    if (picked.length > free) {
      // Refuse the whole pick rather than silently keeping the first `free` files: a list quietly
      // shorter than what was selected is discovered after saving, if at all.
      setUploadError(t('form.attachmentTooMany', { max: MAX_ATTACHMENTS, free }))
      return
    }

    setUploadError(null)
    setProgress({ done: 0, total: picked.length })
    // Collected locally and merged once. Appending per file would write back the list as it was
    // BEFORE the first upload each time, so every file but the last would vanish.
    const uploaded: AttachmentInput[] = []
    try {
      for (const file of picked) {
        const up = await onUpload(file)
        uploaded.push({
          kind: 'FILE',
          filename: up.filename,
          originalName: up.originalName,
          mimeType: up.mimeType,
          sizeBytes: up.sizeBytes,
          label: '',
        })
        setProgress({ done: uploaded.length, total: picked.length })
      }
    } catch (err) {
      setUploadError(getErrorMessage(err))
    } finally {
      // Keep whatever already reached the server, even when a later file failed: those bytes are
      // on disk, and dropping the rows here would leave orphans the user cannot see or remove.
      if (uploaded.length > 0) onChange([...valueRef.current, ...uploaded])
      setProgress(null)
    }
  }

  return (
    <div>
      <label className="block text-sm text-surface-400 mb-1">{t('form.attachments')}</label>
      <p className="text-xs text-surface-500 mb-2">{t('form.attachmentsHint', { max: MAX_ATTACHMENTS })}</p>

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

      {uploadError && <p className="text-sm text-rose-400/80 mt-2" role="alert">{uploadError}</p>}

      {value.length < MAX_ATTACHMENTS && (
        <div className="mt-2 flex flex-wrap gap-4">
          <button
            type="button"
            onClick={addLink}
            // Slots taken by files still in flight are not visible in `value` yet, so without this
            // a link pasted mid-upload merges into a list past the cap that the server refuses.
            disabled={uploading}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors disabled:opacity-60"
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
            {progress && progress.total > 1
              ? t('form.attachmentUploading', { done: progress.done, total: progress.total })
              : t('form.attachmentUpload')}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept={ACCEPT}
            onChange={pickFiles}
            className="hidden"
          />
        </div>
      )}
    </div>
  )
}
