import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Check, LayoutTemplate } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { RpePicker } from './RpePicker'
import { AttachmentEditor } from './AttachmentEditor'
import { adminTrainingCalendarApi } from '../../api/client'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AttachmentInput, CreatePersonalTraining, PersonalTraining, TrainingTemplate } from '../../types'

function addMinutesTo(time: string, minutes: number): string {
  const [h, m] = time.split(':').map(Number)
  const total = Math.min(h * 60 + m + minutes, 23 * 60 + 59)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

function templateToInputs(tpl: TrainingTemplate): AttachmentInput[] {
  return tpl.attachments.map((a): AttachmentInput => {
    const label = a.label ? decodeHtmlEntities(a.label) : ''
    return a.kind === 'FILE'
      ? { kind: 'FILE', filename: a.filename ?? undefined, originalName: a.fileName ?? undefined,
          mimeType: a.mimeType ?? undefined, sizeBytes: a.sizeBytes ?? undefined, label }
      : { kind: 'LINK', url: a.url ?? '', label }
  })
}

export interface InstantCompletion {
  feedback?: string
  rpe?: number
}

// Duplicate flow: create-mode form pre-seeded from an existing training
// (values arrive already decoded — the section decodes before seeding)
export interface TrainingPrefill {
  title: string
  description?: string
  startTime: string
  endTime: string
  // Duplicate carries the source's materials (url + decoded label)
  attachments?: AttachmentInput[]
}

interface TrainingFormModalProps {
  isOpen: boolean
  onClose: () => void
  // Editing an existing training, or creating (optionally with a prefilled date/hour from a grid click)
  training?: PersonalTraining | null
  initialDate?: string
  initialTime?: string
  // Duplicate: seeds a create-mode form with another training's content
  prefill?: TrainingPrefill | null
  // completion set = retroactive logging: create and immediately mark completed
  onSubmit: (data: CreatePersonalTraining, completion?: InstantCompletion | null) => void
  saving: boolean
  // Athlete only — the coach cannot complete trainings
  allowInstantComplete?: boolean
  // Uploads a picked material file (athlete vs coach endpoint)
  onUpload: (file: File) => Promise<{ filename: string; originalName: string; mimeType: string; sizeBytes: number }>
  // Coach-only: offer a "Use template" picker (create mode only)
  templatesEnabled?: boolean
  // Backend rejection (e.g. athlete flag revoked mid-session) — shown above the buttons
  submitError?: string | null
}

export function TrainingFormModal({ isOpen, onClose, training, initialDate, initialTime, prefill, onSubmit, saving, allowInstantComplete, onUpload, templatesEnabled, submitError }: TrainingFormModalProps) {
  const { t } = useTranslation('training')

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={training ? t('form.editTitle') : prefill ? t('form.duplicateTitle') : t('form.addTitle')}
    >
      {/* Mounted only while open — form state resets naturally on every open */}
      {isOpen && (
        <TrainingForm
          training={training}
          initialDate={initialDate}
          initialTime={initialTime}
          prefill={prefill}
          onClose={onClose}
          onSubmit={onSubmit}
          saving={saving}
          allowInstantComplete={allowInstantComplete}
          onUpload={onUpload}
          templatesEnabled={templatesEnabled}
          submitError={submitError}
        />
      )}
    </Modal>
  )
}

const DEFAULT_START = '17:00'
const DEFAULT_DURATION_MIN = 90

function addMinutes(time: string, minutes: number): string {
  const [h, m] = time.split(':').map(Number)
  const total = Math.min(h * 60 + m + minutes, 23 * 60 + 55)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

function TrainingForm({ training, initialDate, initialTime, prefill, onClose, onSubmit, saving, allowInstantComplete, onUpload, templatesEnabled, submitError }: {
  training?: PersonalTraining | null
  initialDate?: string
  initialTime?: string
  prefill?: TrainingPrefill | null
  onClose: () => void
  onSubmit: (data: CreatePersonalTraining, completion?: InstantCompletion | null) => void
  saving: boolean
  allowInstantComplete?: boolean
  onUpload: (file: File) => Promise<{ filename: string; originalName: string; mimeType: string; sizeBytes: number }>
  templatesEnabled?: boolean
  submitError?: string | null
}) {
  const { t } = useTranslation('training')

  const defaultStart = initialTime ?? prefill?.startTime ?? DEFAULT_START
  const [date, setDate] = useState(training?.date ?? initialDate ?? '')
  const [startTime, setStartTime] = useState(training ? training.startTime.slice(0, 5) : defaultStart)
  const [endTime, setEndTime] = useState(
    training ? training.endTime.slice(0, 5) : prefill?.endTime ?? addMinutes(defaultStart, DEFAULT_DURATION_MIN))
  const [title, setTitle] = useState(training ? decodeHtmlEntities(training.title) : prefill?.title ?? '')
  const [description, setDescription] = useState(
    training?.description ? decodeHtmlEntities(training.description) : prefill?.description ?? '')
  const [attachments, setAttachments] = useState<AttachmentInput[]>(
    training
      ? training.attachments.map((a): AttachmentInput => a.kind === 'FILE'
          ? {
              kind: 'FILE',
              filename: a.filename ?? undefined,
              originalName: a.fileName ?? undefined,
              mimeType: a.mimeType ?? undefined,
              sizeBytes: a.sizeBytes ?? undefined,
              label: a.label ? decodeHtmlEntities(a.label) : '',
            }
          : { kind: 'LINK', url: a.url ?? '', label: a.label ? decodeHtmlEntities(a.label) : '' })
      : prefill?.attachments ?? [])
  const [markDone, setMarkDone] = useState(false)
  const [feedback, setFeedback] = useState('')
  const [rpe, setRpe] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Coach create-mode: fill the form from a reusable template (content is copied)
  const showTemplates = !!templatesEnabled && !training
  const templatesQuery = useQuery({
    queryKey: ['admin', 'trainingTemplates'],
    queryFn: adminTrainingCalendarApi.getTemplates,
    enabled: showTemplates,
  })
  const applyTemplate = (id: string) => {
    const tpl = templatesQuery.data?.find((x) => x.id === id)
    if (!tpl) return
    setTitle(decodeHtmlEntities(tpl.title))
    setDescription(tpl.description ? decodeHtmlEntities(tpl.description) : '')
    setEndTime(addMinutesTo(startTime, tpl.defaultDurationMinutes))
    setAttachments(templateToInputs(tpl))
  }

  // Retroactive logging (adding a training after the fact — often days later):
  // offer "mark as completed right away" instead of forcing a second visit to the
  // detail modal. Create mode only, and only once the chosen start has passed
  // (same "must have started" rule the backend enforces).
  const now = new Date()
  const today = format(now, 'yyyy-MM-dd')
  const currentTime = format(now, 'HH:mm')
  const instantCompleteAvailable = !!allowInstantComplete && !training && !!date
    && (date < today || (date === today && startTime <= currentTime))

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) {
      setError(t('form.titleRequired'))
      return
    }
    if (endTime <= startTime) {
      setError(t('form.endAfterStart'))
      return
    }
    // Files pass through as-is; link rows drop blanks and must be http(s)
    const cleanedAttachments: AttachmentInput[] = attachments
      .filter((a) => a.kind === 'FILE' || (a.url ?? '').trim().length > 0)
      .map((a) => a.kind === 'FILE'
        ? { ...a, label: a.label?.trim() || undefined }
        : { kind: 'LINK' as const, url: (a.url ?? '').trim(), label: a.label?.trim() || undefined })
    if (cleanedAttachments.some((a) => a.kind === 'LINK' && !/^https?:\/\//i.test(a.url ?? ''))) {
      setError(t('form.attachmentUrlInvalid'))
      return
    }
    setError(null)
    const completion = instantCompleteAvailable && markDone
      ? { feedback: feedback.trim() || undefined, rpe: rpe ?? undefined }
      : null
    onSubmit({
      date,
      startTime,
      endTime,
      title: title.trim(),
      description: description.trim() || undefined,
      // Form always sends the explicit list (replace on edit, set on create)
      attachments: cleanedAttachments,
    }, completion)
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {showTemplates && (templatesQuery.data?.length ?? 0) > 0 && (
        <div>
          <label className="flex items-center gap-1.5 text-sm text-surface-400 mb-1">
            <LayoutTemplate className="w-3.5 h-3.5" />
            {t('templates.use')}
          </label>
          <select
            defaultValue=""
            onChange={(e) => { if (e.target.value) applyTemplate(e.target.value) }}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          >
            <option value="">{t('templates.usePlaceholder')}</option>
            {templatesQuery.data!.map((tpl) => (
              <option key={tpl.id} value={tpl.id}>
                {decodeHtmlEntities(tpl.title)} · {tpl.defaultDurationMinutes} min
              </option>
            ))}
          </select>
        </div>
      )}

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.date')}</label>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <TimeScrollPicker label={t('form.startTime')} value={startTime} onChange={setStartTime} />
        <TimeScrollPicker label={t('form.endTime')} value={endTime} onChange={setEndTime} />
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.title')}</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder={t('form.titlePlaceholder')}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.description')}</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={2000}
          rows={4}
          placeholder={t('form.descriptionPlaceholder')}
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
        />
      </div>

      <AttachmentEditor value={attachments} onChange={setAttachments} onUpload={onUpload} />

      {instantCompleteAvailable && (
        <div className="p-3 bg-surface-800/60 border border-surface-700 rounded-lg space-y-3">
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={markDone}
              onChange={(e) => setMarkDone(e.target.checked)}
              className="w-4 h-4 accent-green-500"
            />
            <span className="flex items-center gap-1.5 text-sm font-medium text-surface-200">
              <Check className="w-4 h-4 text-green-400" />
              {t('form.markDoneNow')}
            </span>
          </label>

          {markDone && (
            <>
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('completion.feedback')}</label>
                <textarea
                  value={feedback}
                  onChange={(e) => setFeedback(e.target.value)}
                  maxLength={2000}
                  rows={2}
                  placeholder={t('completion.feedbackPlaceholder')}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 resize-none"
                />
              </div>
              <RpePicker value={rpe} onChange={setRpe} />
            </>
          )}
        </div>
      )}

      {(error || submitError) && <p className="text-sm text-rose-400/80">{error ?? submitError}</p>}

      <div className="flex justify-end gap-3 pt-2">
        <Button type="button" variant="secondary" onClick={onClose}>
          {t('form.cancel')}
        </Button>
        <Button type="submit" variant="primary" loading={saving}>
          {t('form.save')}
        </Button>
      </div>
    </form>
  )
}
