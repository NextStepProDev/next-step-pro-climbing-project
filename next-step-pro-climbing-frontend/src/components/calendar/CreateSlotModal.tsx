import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { differenceInCalendarDays, format } from 'date-fns'
import { Link } from 'react-router-dom'
import { adminApi, adminSettlementsApi, adminSiteApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { DateInput } from '../ui/DateInput'
import { TimeScrollPicker } from '../ui/TimeScrollPicker'
import { InvitedUsersPicker } from '../ui/InvitedUsersPicker'
import { SlotKindPicker } from './SlotKindPicker'
import { DayAgendaPreview } from './DayAgendaPreview'
import { CONTRACTOR_SEATS, slotKindFlags, type CreateSlotKind } from '../../utils/slotKind'
import type { CreatedCalendarEntry } from '../../utils/createdEntry'
import type { CreateEventRequest, CreateTimeSlotRequest, InvitedUser } from '../../types'

/** Every kind this form can create, unlike the edit forms — see `CreateSlotKind`. */
const CREATE_KINDS: CreateSlotKind[] = ['REGULAR', 'WINDOW', 'UNAVAILABLE', 'CONTRACTOR']

/**
 * ⚠️ Answering somebody's proposal cannot be a contractor session.
 *
 * The requests panel opens this form with the requester invited and the proposal linked, and
 * creating the slot marks that proposal ACCEPTED. A contractor session has no seats and drops the
 * invitations, so the pair would tell the client "accepted" and leave them with nowhere to sit —
 * and the request is spent, so they cannot even ask again.
 */
const KINDS_FOR_REQUEST: CreateSlotKind[] = ['REGULAR', 'WINDOW', 'UNAVAILABLE']

/**
 * Which of the two rows this form is about to create — see the note on the component.
 *
 * `payoutSourceId` rides along on the slot branch rather than in `CreateTimeSlotRequest` because
 * the server cannot take it there: `AdminService` is outside the settlement packages, and naming a
 * type from them fails the isolation gate (the payout service is package-private, so it would not
 * even compile). The assignment is therefore a second call, made from the same mutation.
 */
type CreateRequest =
  | { target: 'slot'; data: CreateTimeSlotRequest; payoutSourceId?: string }
  | { target: 'event'; data: CreateEventRequest }

interface CreateSlotModalProps {
  isOpen: boolean
  onClose: () => void
  defaultDate: string
  onSuccess?: () => void
  /**
   * The row that was just written, for a caller that wants to show it — the calendar opens its
   * detail modal so a client who is already coming can be written down without hunting for the
   * new entry first. Fired only after a successful create, and before `onClose`.
   */
  onCreated?: (created: CreatedCalendarEntry) => void
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
  onCreated,
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
  const [kind, setKind] = useState<CreateSlotKind>('REGULAR')
  /** Who settles a contractor session. Required for that kind and meaningless for the others. */
  const [payoutSourceId, setPayoutSourceId] = useState('')
  // How long the absence lasts. Both flags stay on screen whenever they can change the outcome,
  // so no hidden state decides what the submit button creates.
  const [multiDay, setMultiDay] = useState(false)
  const [endDate, setEndDate] = useState(defaultDate)
  const [allDay, setAllDay] = useState(false)
  const [invited, setInvited] = useState<InvitedUser[]>(initial?.invited ?? [])

  const queryClient = useQueryClient()

  const isContractor = kind === 'CONTRACTOR'

  // Only once the contractor tile is chosen. This form opens on every "+" in the calendar, and the
  // payer list is of no use to the far more common slot nobody else settles — the same reasoning as
  // `enabled: picking` in the settlement section.
  const { data: payoutSources } = useQuery({
    queryKey: ['admin', 'settlements', 'sources'],
    queryFn: () => adminSettlementsApi.listSources(),
    enabled: isContractor,
  })
  const activeSources = (payoutSources ?? []).filter((source) => !source.archived)

  const createMutation = useMutation({
    // Both endpoints answer with the row they wrote, and the caller needs it: the calendar opens
    // the new entry so people can be added to it straight away, and the fields that decide
    // whether there IS a roster (seats, kind, event type) are already in these responses.
    mutationFn: async (request: CreateRequest): Promise<CreatedCalendarEntry> => {
      if (request.target === 'slot') {
        const slot = await adminApi.createTimeSlot(request.data)
        // ⚠️ In the mutationFn, not onSuccess — the same shape as "save and send invitations": the
        // order is guaranteed and both failures surface on one mutation. A slot that got created
        // and then failed to be assigned is loud here, and lands in "sessions with no payer" if it
        // is missed anyway.
        if (request.payoutSourceId) {
          await adminSettlementsApi.assignSource('slot', slot.id, request.payoutSourceId, null)
        }
        return { target: 'slot', slot }
      }
      return { target: 'event', event: await adminApi.createEvent(request.data) }
    },
    onSuccess: (created, request) => {
      void queryClient.invalidateQueries({ queryKey: ['calendar'] })
      // A new entry can show up on the money screens before anybody prices anything: a contractor
      // session arrives already assigned, and a zero-seat slot created any other way arrives on the
      // "no payer" queue and its calendar marker.
      void queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      // An event shows up in two more places than a slot does — the same three keys the
      // clipboard paste in CalendarPage refreshes.
      if (request.target === 'event') {
        void queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
        void queryClient.invalidateQueries({ queryKey: ['courseEvents'] })
      }
      onCreated?.(created)
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

  const changeKind = (next: CreateSlotKind) => {
    setKind(next)
    // Leaving a range armed behind a hidden field would change what the button creates.
    if (next !== 'UNAVAILABLE') {
      setMultiDay(false)
      setAllDay(false)
    }
    // Same rule for the payer: a contractor left chosen behind a hidden field would file a session
    // under a school that has nothing to do with it.
    if (next !== 'CONTRACTOR') setPayoutSourceId('')
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
    // Naming the payer is the whole point of this kind: a contractor session created without one
    // is exactly the invisible row this tile exists to stop producing.
    if (isContractor && !payoutSourceId) return
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
        // Only a regular slot carries seats; the backend zeroes an unavailable one anyway. A
        // contractor session is stored with none: that is what keeps a client from booking into
        // work already sold, and what makes the session findable afterwards if the payer is lost.
        maxParticipants: isRegular ? form.maxParticipants : isContractor ? CONTRACTOR_SEATS : 1,
        title: title || undefined,
        invitedUserIds: isRegular ? invited.map((u) => u.userId) : [],
        trainingRequestId: initial?.trainingRequestId,
      },
      payoutSourceId: isContractor ? payoutSourceId : undefined,
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
        <SlotKindPicker
          value={kind}
          onChange={changeKind}
          options={initial?.trainingRequestId ? KINDS_FOR_REQUEST : CREATE_KINDS}
        />

        {isContractor && (
          <div>
            <label htmlFor="create-slot-payout-source" className="block text-sm text-surface-400 mb-1">
              {t('createSlot.contractor')}
            </label>
            {activeSources.length > 0 ? (
              <select
                id="create-slot-payout-source"
                value={payoutSourceId}
                onChange={(e) => setPayoutSourceId(e.target.value)}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              >
                <option value="">{t('createSlot.contractorPlaceholder')}</option>
                {activeSources.map((source) => (
                  <option key={source.id} value={source.id}>{source.name}</option>
                ))}
              </select>
            ) : (
              /* Never a dead end: an empty dropdown on a required field is a tile that cannot be
                 used and does not say why. The way out is where contractors are actually made. */
              <p className="text-sm text-surface-400">
                {t('createSlot.contractorNone')}{' '}
                <Link to="/admin/settlements" className="text-primary-400 hover:text-primary-300">
                  {t('createSlot.contractorManage')}
                </Link>
              </p>
            )}
          </div>
        )}

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

        {/* Answering a proposal: what already sits on that day, against the hours as they stand in
            the pickers — so the decision (and the "move it an hour" fix) happens without a second
            tab. Only here: the calendar's own "+" is opened from the day itself. */}
        {initial?.trainingRequestId && !multiDay && (
          <DayAgendaPreview
            date={form.date}
            proposed={showTimes ? { start: form.startTime, end: form.endTime } : null}
          />
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
          <Button
            type="submit"
            loading={createMutation.isPending}
            disabled={isContractor && !payoutSourceId}
            className="flex-1"
          >
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
