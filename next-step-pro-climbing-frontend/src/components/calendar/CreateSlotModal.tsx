import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { differenceInCalendarDays, format } from 'date-fns'
import { adminApi, adminSiteApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { DateInput } from '../ui/DateInput'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { InvitedUsersPicker } from '../ui/InvitedUsersPicker'
import { SlotKindPicker } from './SlotKindPicker'
import { slotKindFlags, type SlotKind } from '../../utils/slotKind'
import type { CreateEventRequest, CreateTimeSlotRequest, InvitedUser } from '../../types'

/** Which of the two rows this form is about to create — see the note on the component. */
type CreateRequest =
  | { target: 'slot'; data: CreateTimeSlotRequest }
  | { target: 'event'; data: CreateEventRequest }

interface CreateSlotModalProps {
  isOpen: boolean
  onClose: () => void
  defaultDate: string
  onSuccess?: () => void
  /** Prefill from a training request: times, seats, the requester invited + a link to the request. */
  initial?: {
    startTime?: string
    endTime?: string
    maxParticipants?: number
    invited?: InvitedUser[]
    trainingRequestId?: string
  }
}

/* An absence can outgrow a slot, and then it stops being one.
 *
 * A slot lives on exactly one date, so a week away used to mean the same form seven times. The
 * model already had the answer — an UNAVAILABLE event spans a range and reads as one continuous
 * absence — but the only door to it was the events panel, next to courses and workshops. So this
 * form decides which of the two it is creating and the admin never picks a noun:
 *
 *   one date + hours  → an unavailable SLOT (the month keeps showing the rest of the day as open)
 *   one date, all day → an UNAVAILABLE EVENT with no times (a slot cannot say "the whole day")
 *   a range of dates  → an UNAVAILABLE EVENT (one row to edit, one click to delete)
 *
 * ⚠️ Over a range the two time pickers stop meaning "these hours, each day". They mean the start
 * and the end of one uninterrupted absence — 18:00 Tuesday until 20:00 Saturday closes four whole
 * nights and days in between. That is what the summary line under the fields spells out; without
 * it the natural reading of "18:00–20:00" is the one the entry does not have.
 */
export function CreateSlotModal({
  isOpen,
  onClose,
  defaultDate,
  onSuccess,
  initial,
}: CreateSlotModalProps) {
  const { t } = useTranslation('calendar')
  const { t: tc } = useTranslation('common')
  const locale = useDateLocale()

  const { data: templates = [] } = useQuery({
    queryKey: ['admin', 'slotTemplates'],
    queryFn: adminSiteApi.getSlotTemplates,
  })

  const [form, setForm] = useState<CreateTimeSlotRequest & { title: string }>({
    date: defaultDate,
    startTime: initial?.startTime ?? '10:00',
    endTime: initial?.endTime ?? '11:00',
    maxParticipants: initial?.maxParticipants ?? 1,
    title: '',
  })
  // The kind lives outside `form`: it is one choice, and the request carries it as two booleans
  // that must never disagree (see slotKindFlags).
  const [kind, setKind] = useState<SlotKind>('REGULAR')
  // How long the absence lasts. Both flags stay on screen whenever they can change the outcome,
  // so no hidden state decides what the submit button creates.
  const [multiDay, setMultiDay] = useState(false)
  const [endDate, setEndDate] = useState(defaultDate)
  const [allDay, setAllDay] = useState(false)
  const [invited, setInvited] = useState<InvitedUser[]>(initial?.invited ?? [])

  const queryClient = useQueryClient()

  const createMutation = useMutation({
    // Nothing here reads the created row, so the two endpoints are collapsed to one void result
    // instead of a union the caller would have to narrow for no reason.
    mutationFn: async (request: CreateRequest) => {
      if (request.target === 'slot') {
        await adminApi.createTimeSlot(request.data)
        return
      }
      await adminApi.createEvent(request.data)
    },
    onSuccess: (_result, request) => {
      void queryClient.invalidateQueries({ queryKey: ['calendar'] })
      // An event shows up in two more places than a slot does — the same three keys the
      // clipboard paste in CalendarPage refreshes.
      if (request.target === 'event') {
        void queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
        void queryClient.invalidateQueries({ queryKey: ['courseEvents'] })
      }
      onSuccess?.()
      onClose()
    },
  })

  const isRegular = kind === 'REGULAR'
  const isUnavailable = kind === 'UNAVAILABLE'
  // A native date field can be emptied (and is empty mid-typing), so nothing downstream may
  // assume a parseable value: `format` on an unparseable date throws, and a throw in render
  // takes the whole modal down.
  const datesFilled = !!form.date && (!multiDay || !!endDate)
  // ISO dates compare as strings.
  const spansDays = isUnavailable && multiDay && datesFilled && endDate > form.date
  const asEvent = isUnavailable && (spansDays || allDay)
  const showTimes = !(isUnavailable && allDay)

  const dateError = isUnavailable && multiDay && datesFilled && endDate < form.date
    ? t('createSlot.endDateAfterStart')
    : null
  // Across a range the two times sit on different days ("Friday 18:00 → Sunday 08:00"), so an end
  // earlier than the start is ordinary there. On a single day it is a window of negative length.
  const timeError = showTimes && !spansDays && form.endTime <= form.startTime
    ? t('createSlot.endAfterStart')
    : null

  const changeKind = (next: SlotKind) => {
    setKind(next)
    // Leaving a range armed behind a hidden field would change what the button creates.
    if (next !== 'UNAVAILABLE') {
      setMultiDay(false)
      setAllDay(false)
    }
  }

  const changeStartDate = (date: string) => {
    setForm({ ...form, date })
    if (endDate < date) setEndDate(date)
  }

  const toggleMultiDay = (next: boolean) => {
    setMultiDay(next)
    // Trips are whole days far more often than not; the hours are the exception you opt into.
    if (next) {
      setAllDay(true)
      if (endDate < form.date) setEndDate(form.date)
    }
  }

  const formatDay = (date: string) => format(parseCalendarDate(date), 'EEE d.MM', { locale })

  const summary = !isUnavailable || !datesFilled ? null
    : spansDays
      ? allDay
        ? t('createSlot.summaryAllDays', {
            days: differenceInCalendarDays(parseCalendarDate(endDate), parseCalendarDate(form.date)) + 1,
            from: formatDay(form.date),
            to: formatDay(endDate),
          })
        : t('createSlot.summaryTimed', {
            from: `${formatDay(form.date)} ${form.startTime}`,
            to: `${formatDay(endDate)} ${form.endTime}`,
          })
      : allDay
        ? t('createSlot.summaryAllDay', { day: formatDay(form.date) })
        : null

  const submitForm = () => {
    if (timeError || dateError || !datesFilled) return
    const { title, ...rest } = form

    if (asEvent) {
      createMutation.mutate({
        target: 'event',
        data: {
          // The backend requires a title; nobody should have to name their own time off.
          title: title.trim() || tc('eventTypes.UNAVAILABLE'),
          eventType: 'UNAVAILABLE',
          startDate: form.date,
          endDate: spansDays ? endDate : form.date,
          maxParticipants: 0,
          startTime: allDay ? undefined : form.startTime,
          endTime: allDay ? undefined : form.endTime,
          invitedUserIds: [],
          // No trainingRequestId on purpose: linking a proposal marks it ACCEPTED, and closing
          // the day is the opposite of accepting it. The request stays pending, to be answered.
        },
      })
      return
    }

    createMutation.mutate({
      target: 'slot',
      data: {
        ...rest,
        ...slotKindFlags(kind),
        // Only a regular slot carries seats; the backend zeroes an unavailable one anyway.
        maxParticipants: isRegular ? form.maxParticipants : 1,
        title: title || undefined,
        invitedUserIds: isRegular ? invited.map((u) => u.userId) : [],
        trainingRequestId: initial?.trainingRequestId,
      },
    })
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('createSlot.title')}>
      <form
        onSubmit={(e) => { e.preventDefault(); submitForm() }}
        className="space-y-4"
      >
        {/* A template is a name and a seat count — neither means anything on an absence. */}
        {templates.length > 0 && !isUnavailable && (
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('createSlot.templateLabel')}</label>
            <select
              value=""
              onChange={(e) => {
                const tpl = templates[Number(e.target.value)]
                if (tpl) {
                  setForm((f) => ({ ...f, title: tpl.name, maxParticipants: tpl.maxParticipants }))
                }
              }}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <option value="">{t('createSlot.templatePlaceholder')}</option>
              {templates.map((tpl, i) => (
                <option key={i} value={i}>
                  {tpl.name} ({tpl.maxParticipants})
                </option>
              ))}
            </select>
          </div>
        )}

        <div>
          <label className="block text-sm text-surface-400 mb-1">
            {isUnavailable ? t('createSlot.absenceTitle') : t('createSlot.slotTitle')}
          </label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder={isUnavailable ? t('createSlot.absenceTitlePlaceholder') : t('createSlot.slotTitlePlaceholder')}
            maxLength={200}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          />
        </div>

        {/* Above the dates and times, because the kind decides which of them are even asked for. */}
        <SlotKindPicker value={kind} onChange={changeKind} />

        <div>
          <div className={multiDay ? 'grid grid-cols-2 gap-4' : undefined}>
            <div>
              <label className="block text-sm text-surface-400 mb-1">
                {multiDay ? t('createSlot.dateFrom') : t('createSlot.date')}
              </label>
              <DateInput
                required
                value={form.date}
                onChange={changeStartDate}
                onKeyUp={(e) => { if (e.key === 'Enter') submitForm() }}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              />
            </div>
            {multiDay && (
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('createSlot.dateTo')}</label>
                <DateInput
                  required
                  value={endDate}
                  onChange={setEndDate}
                  onKeyUp={(e) => { if (e.key === 'Enter') submitForm() }}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
                />
              </div>
            )}
          </div>
          {dateError && (
            <p className="text-sm text-rose-400/80 mt-1">{dateError}</p>
          )}
        </div>

        {isUnavailable && (
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <input
                id="create-slot-multi-day"
                type="checkbox"
                checked={multiDay}
                onChange={(e) => toggleMultiDay(e.target.checked)}
                className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-primary-500 focus:ring-primary-500"
              />
              <label htmlFor="create-slot-multi-day" className="text-sm text-surface-300 cursor-pointer select-none">
                {t('createSlot.multiDay')}
              </label>
            </div>
            <div className="flex items-center gap-2">
              <input
                id="create-slot-all-day"
                type="checkbox"
                checked={allDay}
                onChange={(e) => setAllDay(e.target.checked)}
                className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-primary-500 focus:ring-primary-500"
              />
              <label htmlFor="create-slot-all-day" className="text-sm text-surface-300 cursor-pointer select-none">
                {multiDay ? t('createSlot.wholeDays') : t('createSlot.wholeDay')}
              </label>
            </div>
          </div>
        )}

        {showTimes && (
          <div>
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker
                label={spansDays ? t('createSlot.startsAt', { day: formatDay(form.date) }) : t('createSlot.from')}
                value={form.startTime}
                onChange={(v) => setForm({ ...form, startTime: v })}
              />
              <TimeScrollPicker
                label={spansDays ? t('createSlot.endsAt', { day: formatDay(endDate) }) : t('createSlot.to')}
                value={form.endTime}
                onChange={(v) => setForm({ ...form, endTime: v })}
              />
            </div>
            {timeError && (
              <p className="text-sm text-rose-400/80 mt-1">{timeError}</p>
            )}
          </div>
        )}

        {summary && (
          <p className="text-sm text-slate-300 bg-slate-500/10 border border-slate-500/20 rounded-lg px-3 py-2">
            {summary}
          </p>
        )}

        {isRegular && (
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('createSlot.maxParticipants')}</label>
            <input
              type="number"
              min={0}
              value={form.maxParticipants}
              onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
        )}

        {isRegular && (
          <InvitedUsersPicker value={invited} onChange={setInvited} maxSeats={form.maxParticipants} />
        )}

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={createMutation.isPending} className="flex-1">
            {t('createSlot.submit')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('createSlot.cancel')}
          </Button>
        </div>

        {createMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(createMutation.error)}
          </p>
        )}
      </form>
    </Modal>
  )
}
