import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import clsx from 'clsx'
import { CalendarDays, Check, ClipboardList, Dumbbell, LayoutTemplate } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { RichTextEditor } from '../ui/RichTextEditor'
import { RpePicker } from './RpePicker'
import { AttachmentEditor } from './AttachmentEditor'
import { adminTrainingCalendarApi } from '../../api/client'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { nowInWarsaw } from '../../utils/calendarDate'
import type { AttachmentInput, CreatePersonalTraining, PersonalTraining, TrainingKind, TrainingTemplate } from '../../types'

// Mirrors the CHECK in V77: below 500 and above 10000 is a slipped digit, not a diet.
const MIN_CALORIES = 500
const MAX_CALORIES = 10000

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
  // Duplicating a task must produce a task, not a training
  kind?: TrainingKind
  title: string
  description?: string
  // Null/undefined for an untimed ("all-day") source
  startTime?: string | null
  endTime?: string | null
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
      // The description is a plan someone writes and someone else reads back on a phone;
      // at max-w-lg it wrapped every couple of words. Same width as the detail modal that
      // shows the result, so writing and reading do not disagree about line breaks.
      size="lg"
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

  // "All-day" (untimed) is the default for a fresh create; a grid-hour click, a timed source
  // or editing a timed training turns it off. Editing/duplicating an untimed source keeps it on.
  const initialAllDay = training
    ? training.startTime == null
    : prefill
      ? prefill.startTime == null
      : !initialTime
  const [allDay, setAllDay] = useState(initialAllDay)

  const defaultStart = initialTime ?? prefill?.startTime ?? DEFAULT_START
  const [date, setDate] = useState(training?.date ?? initialDate ?? '')
  // Pickers stay populated even in all-day mode so toggling to timed has sensible values.
  const [startTime, setStartTime] = useState(training?.startTime ? training.startTime.slice(0, 5) : defaultStart)
  const [endTime, setEndTime] = useState(
    training?.endTime ? training.endTime.slice(0, 5) : prefill?.endTime ?? addMinutes(defaultStart, DEFAULT_DURATION_MIN))
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

  // An entry is a training or a task from birth, so the switch only exists while creating one.
  // Changing it later would have to throw a completed training's RPE away.
  const [kind, setKind] = useState<TrainingKind>(training?.kind ?? prefill?.kind ?? 'TRAINING')
  const isTask = kind === 'TASK'
  const [calories, setCalories] = useState(
    training?.targetCalories != null ? String(training.targetCalories) : '')

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
    setAttachments(templateToInputs(tpl))
    // The kind comes with the template: applying "max 2200 kcal" has to produce a task, or the
    // form would hand the backend a training carrying a calorie ceiling and get a 400 back.
    setKind(tpl.kind)
    if (tpl.kind === 'TASK') {
      setCalories(tpl.targetCalories != null ? String(tpl.targetCalories) : '')
      return
    }
    setCalories('')
    // Only a training has a span to prefill; a task never reaches the time pickers
    if (tpl.defaultDurationMinutes != null) setEndTime(addMinutesTo(startTime, tpl.defaultDurationMinutes))
  }

  // Retroactive logging (adding a training after the fact — often days later):
  // offer "mark as completed right away" instead of forcing a second visit to the
  // detail modal. Create mode only, and only once the chosen start has passed
  // (same "must have started" rule the backend enforces).
  const now = nowInWarsaw()
  const today = format(now, 'yyyy-MM-dd')
  const currentTime = format(now, 'HH:mm')
  const instantCompleteAvailable = !!allowInstantComplete && !training && !!date
    && (date < today || (date === today && (isTask || allDay || startTime <= currentTime)))

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim()) {
      setError(t('form.titleRequired'))
      return
    }
    // A task holds for the whole day, so it never reaches the time pickers at all
    if (!isTask && !allDay && endTime <= startTime) {
      setError(t('form.endAfterStart'))
      return
    }
    const trimmedCalories = calories.trim()
    const parsedCalories = isTask && trimmedCalories ? Number(trimmedCalories) : null
    if (parsedCalories != null
        && (!Number.isInteger(parsedCalories) || parsedCalories < MIN_CALORIES || parsedCalories > MAX_CALORIES)) {
      setError(t('form.caloriesRange', { min: MIN_CALORIES, max: MAX_CALORIES }))
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
    // Completing (instant log) requires an RPE
    if (instantCompleteAvailable && markDone && !isTask && rpe == null) {
      setError(t('completion.rpeRequired'))
      return
    }
    setError(null)
    const completion = instantCompleteAvailable && markDone
      ? { feedback: feedback.trim() || undefined, rpe: isTask ? undefined : (rpe ?? undefined) }
      : null
    onSubmit({
      // Only on create: the server ignores it on update, and sending it would imply it can change
      ...(training ? {} : { kind }),
      date,
      // A task and an all-day training both carry no times
      startTime: isTask || allDay ? undefined : startTime,
      endTime: isTask || allDay ? undefined : endTime,
      title: title.trim(),
      description: description.trim() || undefined,
      targetCalories: parsedCalories,
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
                {decodeHtmlEntities(tpl.title)}
                {tpl.kind === 'TASK'
                  ? ` · ${t('form.kind.TASK')}`
                  : ` · ${tpl.defaultDurationMinutes} min`}
              </option>
            ))}
          </select>
        </div>
      )}

      {/* Create only: an entry is a training or a task from birth (see TrainingKind) */}
      {!training && (
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
          <p className="mt-1 text-xs text-surface-500">
            {t(isTask ? 'form.kindTaskHint' : 'form.kindTrainingHint')}
          </p>
        </div>
      )}

      <div>
        <label className="block text-sm text-surface-400 mb-1">{t('form.date')}</label>
        <DateInput
          value={date}
          onChange={setDate}
          required
          className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
        />
      </div>

      {/* A task holds for the whole day, so it has no time controls at all — an hour would put it
          on the week grid at a position claiming something it does not have. */}
      {!isTask && (
        <>
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={allDay}
              onChange={(e) => setAllDay(e.target.checked)}
              className="w-4 h-4 accent-primary-500"
            />
            <span className="flex items-center gap-1.5 text-sm font-medium text-surface-200">
              <CalendarDays className="w-4 h-4 text-surface-400" />
              {t('form.allDay')}
            </span>
          </label>

          {!allDay && (
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker label={t('form.startTime')} value={startTime} onChange={setStartTime} />
              <TimeScrollPicker label={t('form.endTime')} value={endTime} onChange={setEndTime} />
            </div>
          )}
        </>
      )}

      {isTask && (
        <div>
          <label htmlFor="training-calories" className="block text-sm text-surface-400 mb-1">
            {t('form.calories')}
          </label>
          <input
            id="training-calories"
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
      )}

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
        <RichTextEditor
          value={description}
          onChange={setDescription}
          maxLength={2000}
          rows={8}
          placeholder={t('form.descriptionPlaceholder')}
          // A 2000-character field in four rows is a peephole over your own text: while
          // writing a plan you could only see the last sentence. Taller, plus resize-y so a
          // long plan can be pulled open — the modal is the scroll container anyway.
          inputClassName="w-full bg-surface-800 border border-surface-600 rounded-b px-4 py-3 text-surface-100 leading-relaxed resize-y min-h-40 focus:outline-none focus:border-primary-500"
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
                  rows={4}
                  placeholder={t('completion.feedbackPlaceholder')}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-sm text-surface-100 leading-relaxed resize-y"
                />
              </div>
              {/* A task is ticked off, never rated: perceived effort is a question about a
                  session, and an answer here would land in the RPE averages. */}
              {!isTask && <RpePicker value={rpe} onChange={setRpe} />}
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
