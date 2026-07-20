import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Clock, Paperclip, Pencil, Plus, Trash2 } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { AttachmentEditor } from './AttachmentEditor'
import { adminTrainingCalendarApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AttachmentInput, SaveTrainingTemplate, TrainingTemplate } from '../../types'

const TEMPLATES_KEY = ['admin', 'trainingTemplates']

interface TrainingTemplatesModalProps {
  isOpen: boolean
  onClose: () => void
}

/** Coach's reusable template library: list + create/edit/delete. Shared across all athletes. */
export function TrainingTemplatesModal({ isOpen, onClose }: TrainingTemplatesModalProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  // null = list view; 'new' or a template = form view
  const [editing, setEditing] = useState<TrainingTemplate | 'new' | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<TrainingTemplate | null>(null)

  const templatesQuery = useQuery({
    queryKey: TEMPLATES_KEY,
    queryFn: adminTrainingCalendarApi.getTemplates,
    enabled: isOpen,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: TEMPLATES_KEY })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminTrainingCalendarApi.deleteTemplate(id),
    onSuccess: () => { setConfirmDelete(null); invalidate() },
  })

  const templates = templatesQuery.data ?? []

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => { setEditing(null); onClose() }}
      title={editing ? (editing === 'new' ? t('templates.add') : t('templates.edit')) : t('templates.title')}
      size="lg"
    >
      {editing ? (
        <TemplateForm
          template={editing === 'new' ? null : editing}
          onDone={() => { setEditing(null); invalidate() }}
          onCancel={() => setEditing(null)}
        />
      ) : templatesQuery.isLoading ? (
        <div className="py-10 flex justify-center"><LoadingSpinner /></div>
      ) : (
        <div className="space-y-3">
          <Button variant="primary" size="sm" onClick={() => setEditing('new')}>
            <Plus className="w-4 h-4 mr-1" />
            {t('templates.add')}
          </Button>

          {templates.length === 0 ? (
            <p className="text-sm text-surface-400 py-4 text-center">{t('templates.empty')}</p>
          ) : (
            <ul className="space-y-2">
              {templates.map((tpl) => (
                <li key={tpl.id} className="flex items-center gap-3 p-3 rounded-lg border border-surface-800 bg-surface-900">
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-surface-100 truncate">{decodeHtmlEntities(tpl.title)}</p>
                    <p className="text-xs text-surface-500 flex items-center gap-3 mt-0.5">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="w-3 h-3" />{tpl.defaultDurationMinutes} min
                      </span>
                      {tpl.attachments.length > 0 && (
                        <span className="inline-flex items-center gap-1">
                          <Paperclip className="w-3 h-3" />{tpl.attachments.length}
                        </span>
                      )}
                    </p>
                  </div>
                  <button
                    onClick={() => setEditing(tpl)}
                    className="p-1.5 rounded-lg text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition-colors"
                    title={t('templates.edit')}
                  >
                    <Pencil className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setConfirmDelete(tpl)}
                    className="p-1.5 rounded-lg text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
                    title={t('templates.delete')}
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <ConfirmModal
        isOpen={confirmDelete !== null}
        onClose={() => setConfirmDelete(null)}
        onConfirm={() => { if (confirmDelete) deleteMutation.mutate(confirmDelete.id) }}
        title={t('templates.deleteConfirmTitle')}
        message={t('templates.deleteConfirmMessage')}
        variant="danger"
      />
    </Modal>
  )
}

const DEFAULT_DURATION = 90

function templateToInputs(tpl: TrainingTemplate): AttachmentInput[] {
  return tpl.attachments.map((a): AttachmentInput => {
    const label = a.label ? decodeHtmlEntities(a.label) : ''
    return a.kind === 'FILE'
      ? {
          kind: 'FILE',
          filename: a.filename ?? undefined,
          originalName: a.fileName ?? undefined,
          mimeType: a.mimeType ?? undefined,
          sizeBytes: a.sizeBytes ?? undefined,
          label,
        }
      : { kind: 'LINK', url: a.url ?? '', label }
  })
}

function TemplateForm({ template, onDone, onCancel }: {
  template: TrainingTemplate | null
  onDone: () => void
  onCancel: () => void
}) {
  const { t } = useTranslation('training')
  const [title, setTitle] = useState(template ? decodeHtmlEntities(template.title) : '')
  const [description, setDescription] = useState(template?.description ? decodeHtmlEntities(template.description) : '')
  const [duration, setDuration] = useState(template?.defaultDurationMinutes ?? DEFAULT_DURATION)
  const [attachments, setAttachments] = useState<AttachmentInput[]>(template ? templateToInputs(template) : [])
  const [error, setError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: (data: SaveTrainingTemplate) =>
      template ? adminTrainingCalendarApi.updateTemplate(template.id, data) : adminTrainingCalendarApi.createTemplate(data),
    onSuccess: onDone,
    onError: (err) => setError(getErrorMessage(err)),
  })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) { setError(t('templates.titleRequired')); return }
    if (duration < 15 || duration > 720) { setError(t('templates.durationRange')); return }
    const cleaned: AttachmentInput[] = attachments
      .filter((a) => a.kind === 'FILE' || (a.url ?? '').trim().length > 0)
      .map((a) => a.kind === 'FILE'
        ? { ...a, label: a.label?.trim() || undefined }
        : { kind: 'LINK' as const, url: (a.url ?? '').trim(), label: a.label?.trim() || undefined })
    if (cleaned.some((a) => a.kind === 'LINK' && !/^https?:\/\//i.test(a.url ?? ''))) {
      setError(t('form.attachmentUrlInvalid')); return
    }
    setError(null)
    saveMutation.mutate({ title: title.trim(), description: description.trim() || undefined, defaultDurationMinutes: duration, attachments: cleaned })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('templates.form.title')}</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder={t('templates.form.titlePlaceholder')}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('templates.form.duration')}</label>
        <input
          type="number"
          value={duration}
          onChange={(e) => setDuration(Number(e.target.value))}
          min={15}
          max={720}
          step={5}
          className="w-32 bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.description')}</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={2000}
          rows={3}
          placeholder={t('form.descriptionPlaceholder')}
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
        />
      </div>

      <AttachmentEditor value={attachments} onChange={setAttachments} onUpload={adminTrainingCalendarApi.uploadAttachment} />

      {error && <p className="text-sm text-rose-400/80">{error}</p>}

      <div className="flex justify-end gap-3 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>{t('form.cancel')}</Button>
        <Button type="submit" variant="primary" loading={saveMutation.isPending}>{t('form.save')}</Button>
      </div>
    </form>
  )
}
