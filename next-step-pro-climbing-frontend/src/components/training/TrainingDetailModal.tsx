import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, CopyPlus, ExternalLink, FileText, Paperclip, Pencil, Trash2, RotateCcw, UserCog, User as UserIcon, X } from 'lucide-react'
import { format } from 'date-fns'
import clsx from 'clsx'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { ConfirmModal } from '../ui/ConfirmModal'
import { RpePicker } from './RpePicker'
import { CommentThread } from './CommentThread'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { PersonalTraining, PersonalTrainingStatus, TrainingAttachment } from '../../types'

interface TrainingDetailModalProps {
  training: PersonalTraining | null
  onClose: () => void
  api: TrainingCalendarAdapter
  // Coach view: completion is read-only (athlete's feedback), provenance badge inverted
  isCoachView?: boolean
  onEdit: (training: PersonalTraining) => void
  onDuplicate: (training: PersonalTraining) => void
  onDelete: (training: PersonalTraining) => void
  onComplete?: (training: PersonalTraining, data: { feedback?: string; rpe?: number }) => void
  onUncomplete?: (training: PersonalTraining) => void
  mutating?: boolean
  onCommentPosted?: () => void
  // Backend rejection of delete/complete/uncomplete — shown above the footer
  errorMessage?: string | null
}

// Completion is only possible once the training has started (backend enforces the same rule)
function hasStarted(training: PersonalTraining): boolean {
  return new Date(`${training.date}T${training.startTime}`) <= new Date()
}

function statusChip(status: PersonalTrainingStatus): string {
  switch (status) {
    case 'COMPLETED':
      return 'bg-green-500/20 text-green-300 border-green-500/40'
    case 'MISSED':
      return 'bg-rose-500/15 text-rose-300/90 border-rose-500/40'
    default:
      return 'bg-indigo-500/20 text-indigo-300 border-indigo-500/40'
  }
}

export function TrainingDetailModal({
  training, onClose, api, isCoachView, onEdit, onDuplicate, onDelete,
  onComplete, onUncomplete, mutating, onCommentPosted, errorMessage,
}: TrainingDetailModalProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  const [confirmDelete, setConfirmDelete] = useState(false)
  const [completionOpen, setCompletionOpen] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [rpe, setRpe] = useState<number | null>(null)

  if (!training) return null

  const statusLabel = t(`status.${training.status.toLowerCase()}`)

  const openCompletionForm = () => {
    setFeedback(training.feedback ? decodeHtmlEntities(training.feedback) : '')
    setRpe(training.rpe)
    setCompletionOpen(true)
  }

  const saveCompletion = () => {
    onComplete?.(training, {
      feedback: feedback.trim() || undefined,
      rpe: rpe ?? undefined,
    })
    setCompletionOpen(false)
  }

  return (
    <Modal isOpen onClose={onClose} title={decodeHtmlEntities(training.title)} size="lg">
      <div className="space-y-5">
        {/* Header: date, time, status, provenance */}
        <div className="flex flex-wrap items-center gap-2">
          <span className={clsx('px-2 py-0.5 text-xs font-medium border rounded-full', statusChip(training.status))}>
            {statusLabel}
          </span>
          <span className="text-sm text-surface-300 capitalize">
            {format(new Date(training.date), 'EEEE, d MMMM yyyy', { locale })}
          </span>
          <span className="text-sm text-surface-400">
            {training.startTime.slice(0, 5)} - {training.endTime.slice(0, 5)}
          </span>
          <span className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] text-surface-400 bg-surface-800 border border-surface-700 rounded-full">
            {training.createdByAdmin ? <UserCog className="w-3 h-3" /> : <UserIcon className="w-3 h-3" />}
            {training.createdByAdmin ? t('detail.addedByCoach') : t('detail.addedByAthlete')}
          </span>
        </div>

        {training.description && (
          <p className="text-sm text-surface-300 whitespace-pre-wrap">{decodeHtmlEntities(training.description)}</p>
        )}

        {/* Materials: embedded YouTube/Instagram players + plain link cards */}
        {training.attachments.length > 0 && (
          <div className="space-y-2">
            <div className="flex items-center gap-1.5 text-sm font-medium text-surface-300">
              <Paperclip className="w-4 h-4 text-surface-400" />
              {t('detail.materials')}
            </div>
            {training.attachments.map((att) => (
              <MaterialItem key={att.id} attachment={att} />
            ))}
          </div>
        )}

        {/* Completion */}
        <div className="p-3 bg-surface-800/60 border border-surface-700 rounded-lg space-y-3">
          {training.status === 'COMPLETED' && !completionOpen ? (
            <>
              <div className="flex items-center gap-2 text-green-300 text-sm font-medium">
                <Check className="w-4 h-4" />
                {t('completion.completedAt')}
                {training.completedAt && (
                  <span className="text-surface-400 font-normal">
                    {format(new Date(training.completedAt), 'dd.MM.yyyy HH:mm')}
                  </span>
                )}
                {training.rpe != null && (
                  <span className="ml-auto px-2 py-0.5 text-xs bg-surface-700 rounded-full">RPE {training.rpe}/10</span>
                )}
              </div>
              {training.feedback ? (
                <p className="text-sm text-surface-300 whitespace-pre-wrap">
                  {isCoachView && <span className="block text-xs text-surface-500 mb-0.5">{t('completion.athleteFeedback')}</span>}
                  {decodeHtmlEntities(training.feedback)}
                </p>
              ) : (
                isCoachView && <p className="text-sm text-surface-500">{t('completion.noFeedback')}</p>
              )}
              {!isCoachView && (
                <div className="flex gap-2">
                  <Button size="sm" variant="secondary" onClick={openCompletionForm}>
                    <Pencil className="w-3.5 h-3.5 mr-1" />
                    {t('completion.edit')}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => onUncomplete?.(training)} loading={mutating}>
                    <RotateCcw className="w-3.5 h-3.5 mr-1" />
                    {t('completion.undo')}
                  </Button>
                </div>
              )}
            </>
          ) : !isCoachView && completionOpen ? (
            <div className="space-y-3">
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('completion.feedback')}</label>
                <textarea
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  maxLength={2000}
                  rows={3}
                  placeholder={t('completion.feedbackPlaceholder')}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 resize-none"
                />
              </div>
              <RpePicker value={rpe} onChange={setRpe} />
              <div className="flex justify-end gap-2">
                <Button size="sm" variant="secondary" onClick={() => setCompletionOpen(false)}>
                  {t('form.cancel')}
                </Button>
                <Button size="sm" variant="primary" onClick={saveCompletion} loading={mutating}>
                  {t('completion.save')}
                </Button>
              </div>
            </div>
          ) : !isCoachView && hasStarted(training) ? (
            <Button variant="primary" size="sm" onClick={openCompletionForm}>
              <Check className="w-4 h-4 mr-1" />
              {t('completion.markDone')}
            </Button>
          ) : !isCoachView ? (
            <p className="text-sm text-surface-500">{t('completion.availableAfterStart')}</p>
          ) : (
            <p className="text-sm text-surface-500">{statusLabel}</p>
          )}
        </div>

        {/* Comment thread */}
        <CommentThread trainingId={training.id} api={api} onPosted={onCommentPosted} />

        {errorMessage && (
          <p className="text-sm text-rose-400/80">{errorMessage}</p>
        )}

        {/* Footer: close / edit / delete */}
        <div className="flex justify-end gap-2 pt-2 border-t border-surface-800">
          <Button variant="ghost" size="sm" onClick={onClose}>
            <X className="w-3.5 h-3.5 mr-1" />
            {t('detail.close')}
          </Button>
          <Button variant="secondary" size="sm" onClick={() => onDuplicate(training)}>
            <CopyPlus className="w-3.5 h-3.5 mr-1" />
            {t('detail.duplicate')}
          </Button>
          <Button variant="secondary" size="sm" onClick={() => onEdit(training)}>
            <Pencil className="w-3.5 h-3.5 mr-1" />
            {t('detail.edit')}
          </Button>
          <Button variant="danger" size="sm" onClick={() => setConfirmDelete(true)}>
            <Trash2 className="w-3.5 h-3.5 mr-1" />
            {t('detail.delete')}
          </Button>
        </div>
      </div>

      <ConfirmModal
        isOpen={confirmDelete}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() => {
          setConfirmDelete(false)
          onDelete(training)
        }}
        title={t('detail.deleteConfirmTitle')}
        message={t('detail.deleteConfirmMessage')}
        variant="danger"
      />
    </Modal>
  )
}

// One material: an embedded YouTube/Instagram player, an inline image, a PDF/file card, or a link card.
function MaterialItem({ attachment }: { attachment: TrainingAttachment }) {
  const { url, label, embedUrl, kind, mimeType, fileName } = attachment
  const decodedLabel = label ? decodeHtmlEntities(label) : null

  // LINK → embedded YouTube/Instagram player
  if (kind === 'LINK' && embedUrl) {
    const instagram = embedUrl.includes('instagram.com')
    return (
      <figure className="space-y-1">
        {decodedLabel && <figcaption className="text-xs text-surface-400">{decodedLabel}</figcaption>}
        <div
          className={clsx('w-full', instagram ? 'max-w-[400px] mx-auto' : '')}
          style={instagram ? { height: 560 } : { aspectRatio: '16 / 9' }}
        >
          <iframe
            src={embedUrl}
            title={decodedLabel ?? url ?? 'video'}
            className="w-full h-full rounded-lg border border-surface-800"
            allowFullScreen
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            loading="lazy"
          />
        </div>
      </figure>
    )
  }

  // FILE image → inline preview
  if (kind === 'FILE' && mimeType?.startsWith('image/') && url) {
    return (
      <figure className="space-y-1">
        {decodedLabel && <figcaption className="text-xs text-surface-400">{decodedLabel}</figcaption>}
        <a href={url} target="_blank" rel="noopener noreferrer">
          <img src={url} alt={decodedLabel ?? fileName ?? 'image'} className="max-h-80 rounded-lg border border-surface-800" />
        </a>
      </figure>
    )
  }

  // FILE (pdf/other) or plain LINK → clickable card
  const isFile = kind === 'FILE'
  const primary = decodedLabel ?? (isFile ? fileName : url) ?? ''
  const secondary = decodedLabel ? (isFile ? fileName : url) : (isFile ? null : null)
  const Icon = isFile ? FileText : ExternalLink

  return (
    <a
      href={url ?? '#'}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-2.5 p-2.5 rounded-lg border border-surface-700 bg-surface-800/60 hover:bg-surface-800 hover:border-surface-600 transition-colors"
    >
      <Icon className={clsx('w-4 h-4 shrink-0', isFile ? 'text-rose-300' : 'text-primary-400')} />
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-medium text-surface-100 truncate">{primary}</span>
        {secondary && <span className="block text-xs text-surface-500 truncate">{secondary}</span>}
      </span>
    </a>
  )
}
