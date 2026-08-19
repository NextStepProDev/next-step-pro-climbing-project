import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { ClipboardList, Dumbbell } from 'lucide-react'
import { Button } from '../ui/Button'
import { AttachmentEditor } from './AttachmentEditor'
import { adminTrainingCalendarApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AttachmentInput, SaveTrainingTemplate, TrainingKind, TrainingTemplate } from '../../types'

const DEFAULT_DURATION = 90
const MIN_DURATION = 15
const MAX_DURATION = 720
// Mirrors the CHECK in V77/V78: below 500 and above 10000 is a slipped digit, not a diet.
const MIN_CALORIES = 500
const MAX_CALORIES = 10000

/** Starting values for a template built from an existing entry ("save this one as a template"). */
export interface TemplateDraft {
  kind: TrainingKind
  title: string
  description?: string
  defaultDurationMinutes?: number | null
  targetCalories?: number | null
  attachments: AttachmentInput[]
}

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

/**
 * Create/edit form for one library template. Used by the template manager and by "save as
 * template" in an entry's detail view, which passes a draft copied from that entry.
 *
 * The kind switch stays available while EDITING, unlike the one on an entry: that one is frozen
 * because flipping a completed training would have to discard its RPE, and a template has no
 * completion, rating or history to lose.
 */
export function TrainingTemplateForm({ template, draft, onDone, onCancel }: {
  template?: TrainingTemplate | null
  draft?: TemplateDraft | null
  onDone: () => void
  onCancel: () => void
}) {
  const { t } = useTranslation('training')
  const source = template ?? draft ?? null

  const [kind, setKind] = useState<TrainingKind>(source?.kind ?? 'TRAINING')
  const [title, setTitle] = useState(
    template ? decodeHtmlEntities(template.title) : draft?.title ?? '')
  const [description, setDescription] = useState(
    template?.description ? decodeHtmlEntities(template.description) : draft?.description ?? '')
  // Kept populated across a switch to TASK so switching back does not lose the number
  const [duration, setDuration] = useState(source?.defaultDurationMinutes ?? DEFAULT_DURATION)
  const [calories, setCalories] = useState(
    source?.targetCalories != null ? String(source.targetCalories) : '')
  const [attachments, setAttachments] = useState<AttachmentInput[]>(
    template ? templateToInputs(template) : draft?.attachments ?? [])
  const [error, setError] = useState<string | null>(null)

  const isTask = kind === 'TASK'

  const saveMutation = useMutation({
    mutationFn: (data: SaveTrainingTemplate) =>
      template ? adminTrainingCalendarApi.updateTemplate(template.id, data) : adminTrainingCalendarApi.createTemplate(data),
    onSuccess: onDone,
    onError: (err) => setError(getErrorMessage(err)),
  })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) { setError(t('templates.titleRequired')); return }
    if (!isTask && (duration < MIN_DURATION || duration > MAX_DURATION)) {
      setError(t('templates.durationRange')); return
    }
    const trimmedCalories = calories.trim()
    const parsedCalories = isTask && trimmedCalories ? Number(trimmedCalories) : null
    if (parsedCalories != null
        && (!Number.isInteger(parsedCalories) || parsedCalories < MIN_CALORIES || parsedCalories > MAX_CALORIES)) {
      setError(t('form.caloriesRange', { min: MIN_CALORIES, max: MAX_CALORIES })); return
    }
    const cleaned: AttachmentInput[] = attachments
      .filter((a) => a.kind === 'FILE' || (a.url ?? '').trim().length > 0)
      .map((a) => a.kind === 'FILE'
        ? { ...a, label: a.label?.trim() || undefined }
        : { kind: 'LINK' as const, url: (a.url ?? '').trim(), label: a.label?.trim() || undefined })
    if (cleaned.some((a) => a.kind === 'LINK' && !/^https?:\/\//i.test(a.url ?? ''))) {
      setError(t('form.attachmentUrlInvalid')); return
    }
    setError(null)
    saveMutation.mutate({
      kind,
      title: title.trim(),
      description: description.trim() || undefined,
      // One shape per kind, exactly as the backend demands: a task brings a ceiling and no span
      defaultDurationMinutes: isTask ? null : duration,
      targetCalories: parsedCalories,
      attachments: cleaned,
    })
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      <div>
        <span className="block text-sm text-surface-400 mb-1">{t('form.kindLabel')}</span>
        <div className="flex gap-1" role="group" aria-label={t('form.kindLabel')}>
          {(['TRAINING', 'TASK'] as const).map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={kind === option}
              onClick={() => setKind(option)}
              className={clsx(
                'flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-lg border transition-colors',
                kind === option
                  ? 'bg-primary-600 border-primary-500 text-white'
                  : 'bg-surface-800 border-surface-700 text-surface-400 hover:text-surface-200',
              )}
            >
              {option === 'TASK' ? <ClipboardList className="w-4 h-4" /> : <Dumbbell className="w-4 h-4" />}
              {t(`form.kind.${option}`)}
            </button>
          ))}
        </div>
      </div>

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('templates.form.title')}</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
          placeholder={t(isTask ? 'templates.form.titlePlaceholderTask' : 'templates.form.titlePlaceholder')}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      {/* A whole-day commitment has no span to prefill, so a task template shows no duration
          at all rather than a disabled field claiming it has one. */}
      {isTask ? (
        <div>
          <label htmlFor="template-calories" className="block text-sm text-surface-400 mb-1">
            {t('form.calories')}
          </label>
          <input
            id="template-calories"
            type="number"
            inputMode="numeric"
            min={MIN_CALORIES}
            max={MAX_CALORIES}
            value={calories}
            onChange={(e) => setCalories(e.target.value)}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          />
          <p className="mt-1 text-xs text-surface-500">{t('form.caloriesHint')}</p>
        </div>
      ) : (
        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('templates.form.duration')}</label>
          <input
            type="number"
            value={duration}
            onChange={(e) => setDuration(Number(e.target.value))}
            min={MIN_DURATION}
            max={MAX_DURATION}
            step={5}
            className="w-32 bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          />
        </div>
      )}

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.description')}</label>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={2000}
          rows={8}
          placeholder={t('form.descriptionPlaceholder')}
          // Same reasoning as the training form: a template body is the longest text in this
          // feature, and it is written once to be reused, so it deserves to be readable while
          // being written. resize-y for the ones that outgrow eight rows.
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-3 text-surface-100 leading-relaxed resize-y min-h-40"
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
