import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { addDays, differenceInCalendarDays, format } from 'date-fns'
import { CalendarClock } from 'lucide-react'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { DateInput } from '../ui/DateInput'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { SuccessCheckmark } from '../ui/SuccessCheckmark'
import { ModalCancelButton } from '../ui/ModalCancelButton'
import { rescheduleApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { nowInWarsaw, parseCalendarDate, todayInWarsaw } from '../../utils/calendarDate'
import { useDirty } from '../../hooks/useDirty'
import { useChildDirty } from '../../hooks/useChildDirty'
import { describeTerm } from '../../utils/rescheduleTerm'

/** What is being moved: one booked training, or a whole (possibly multi-day) event. */
export type RescheduleTarget =
  | { kind: 'slot'; reservationId: string; title: string | null; date: string; startTime: string; endTime: string }
  | {
      kind: 'event'; eventId: string; title: string
      startDate: string; endDate: string; startTime: string | null; endTime: string | null
    }

interface RescheduleRequestModalProps {
  target: RescheduleTarget | null
  onClose: () => void
}

/**
 * "Change the time" for a booked client. The form starts at the current term, so the usual case —
 * same hours, another day — is one change of the date field. Nothing moves until the coach accepts;
 * the booking stands meanwhile, and the modal says so, because a client who thinks they have
 * already moved will turn up on the wrong day.
 */
export function RescheduleRequestModal({ target, onClose }: RescheduleRequestModalProps) {
  const { t } = useTranslation('reservations')
  const [isDirty, reportDirty] = useChildDirty(!!target)
  return (
    <Modal isOpen={!!target} onClose={onClose} title={t('reschedule.title')} confirmClose={isDirty}>
      {/* Keyed by target so the form starts from the term of the card that opened it, every time. */}
      {target && (
        <RescheduleForm
          key={target.kind === 'slot' ? target.reservationId : target.eventId}
          target={target}
          onClose={onClose}
          onDirtyChange={reportDirty}
        />
      )}
    </Modal>
  )
}

function initialForm(target: RescheduleTarget) {
  return target.kind === 'slot'
    ? { date: target.date, endDate: target.date, startTime: target.startTime.slice(0, 5), endTime: target.endTime.slice(0, 5), comment: '' }
    : {
        date: target.startDate,
        endDate: target.endDate,
        startTime: target.startTime?.slice(0, 5) ?? '',
        endTime: target.endTime?.slice(0, 5) ?? '',
        comment: '',
      }
}

function RescheduleForm({ target, onClose, onDirtyChange }: {
  target: RescheduleTarget
  onClose: () => void
  onDirtyChange: (dirty: boolean) => void
}) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [form, setForm] = useState(() => initialForm(target))
  const [initial] = useState(() => initialForm(target))
  const [showSuccess, setShowSuccess] = useState(false)
  const formDirty = useDirty(form)
  // Reported during render, not from an effect — see useChildDirty.
  onDirtyChange(formDirty && !showSuccess)

  const isEvent = target.kind === 'event'
  const hasHours = target.kind === 'slot' || (target.startTime != null && target.endTime != null)
  const multiDay = isEvent && target.startDate !== target.endDate
  // A course keeps its length: moving the first day carries the last one along.
  const lengthDays = isEvent ? differenceInCalendarDays(parseCalendarDate(target.endDate), parseCalendarDate(target.startDate)) : 0

  const mutation = useMutation({
    mutationFn: rescheduleApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations', 'reschedule'] })
      setShowSuccess(true)
    },
  })

  const unchanged = form.date === initial.date && form.endDate === initial.endDate
    && form.startTime === initial.startTime && form.endTime === initial.endTime
  const timeError = hasHours && !multiDay && form.endTime <= form.startTime ? t('reschedule.endAfterStart') : null
  const pastError = (() => {
    if (!form.date) return null
    const today = todayInWarsaw()
    if (form.date < today) return t('reschedule.inPast')
    if (form.date === today && hasHours) {
      const now = nowInWarsaw()
      const nowHm = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
      if (form.startTime <= nowHm) return t('reschedule.inPast')
    }
    return null
  })()
  const error = timeError ?? pastError

  const submit = () => {
    if (error || unchanged || !form.date) return
    mutation.mutate(target.kind === 'slot'
      ? {
          reservationId: target.reservationId,
          newDate: form.date,
          newStartTime: form.startTime,
          newEndTime: form.endTime,
          comment: form.comment.trim() || undefined,
        }
      : {
          eventId: target.eventId,
          newDate: form.date,
          newEndDate: form.endDate,
          newStartTime: hasHours ? form.startTime : undefined,
          newEndTime: hasHours ? form.endTime : undefined,
          comment: form.comment.trim() || undefined,
        })
  }

  const current = target.kind === 'slot'
    ? describeTerm(locale, target.date, null, target.startTime, target.endTime)
    : describeTerm(locale, target.startDate, target.endDate, target.startTime, target.endTime)

  return (
    <>
      {showSuccess && <SuccessCheckmark onDone={() => { setShowSuccess(false); onClose() }} />}
      <form onSubmit={(e) => { e.preventDefault(); submit() }} className="space-y-4">
        <div className="p-3 bg-surface-800/60 border border-surface-700 rounded-lg">
          <div className="flex items-center gap-2 text-sm text-surface-300">
            <CalendarClock className="w-4 h-4 text-primary-400 shrink-0" aria-hidden="true" />
            <span className="font-medium">{target.title ?? t('trainings')}</span>
          </div>
          <p className="text-sm text-surface-400 mt-1">
            {t('reschedule.current')} <span className="text-surface-200">{current}</span>
          </p>
        </div>

        <div className={multiDay ? 'grid grid-cols-2 gap-4' : undefined}>
          <div>
            <label htmlFor="reschedule-date" className="block text-sm text-surface-400 mb-1">
              {multiDay ? t('reschedule.fromDay') : t('reschedule.newDate')}
            </label>
            <DateInput
              id="reschedule-date"
              value={form.date}
              min={todayInWarsaw()}
              onChange={(date) => setForm((f) => ({
                ...f,
                date,
                endDate: date && isEvent ? format(addDays(parseCalendarDate(date), lengthDays), 'yyyy-MM-dd') : date,
              }))}
              required
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
          {multiDay && (
            <div>
              <label htmlFor="reschedule-end-date" className="block text-sm text-surface-400 mb-1">{t('reschedule.toDay')}</label>
              <DateInput
                id="reschedule-end-date"
                value={form.endDate}
                min={form.date || todayInWarsaw()}
                onChange={(endDate) => setForm((f) => ({ ...f, endDate }))}
                required
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              />
            </div>
          )}
        </div>

        {hasHours && (
          <div className="grid grid-cols-2 gap-4">
            <TimeScrollPicker
              label={t('reschedule.from')}
              value={form.startTime}
              onChange={(v) => setForm((f) => ({ ...f, startTime: v }))}
            />
            <TimeScrollPicker
              label={t('reschedule.to')}
              value={form.endTime}
              onChange={(v) => setForm((f) => ({ ...f, endTime: v }))}
            />
          </div>
        )}
        {error && <p className="text-sm text-rose-400/80">{error}</p>}

        <div>
          <label htmlFor="reschedule-comment" className="block text-sm text-surface-400 mb-1">{t('reschedule.comment')}</label>
          <textarea
            id="reschedule-comment"
            value={form.comment}
            onChange={(e) => setForm((f) => ({ ...f, comment: e.target.value }))}
            placeholder={t('reschedule.commentPlaceholder')}
            maxLength={1000}
            rows={3}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
          />
        </div>

        <p className="text-xs text-surface-400">{t('reschedule.hint')}</p>

        <div className="flex gap-3 pt-1">
          <Button type="submit" loading={mutation.isPending} disabled={unchanged || !!error} className="flex-1">
            {t('reschedule.submit')}
          </Button>
          <ModalCancelButton onClose={onClose}>{t('reschedule.cancel')}</ModalCancelButton>
        </div>
        {mutation.isError && (
          <p className="text-sm text-rose-400/80">{getErrorMessage(mutation.error)}</p>
        )}
      </form>
    </>
  )
}
