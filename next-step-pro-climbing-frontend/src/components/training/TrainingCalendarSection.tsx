import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, Lock, Trash2, X } from 'lucide-react'
import { format, startOfWeek, startOfMonth, endOfMonth, addDays } from 'date-fns'
import clsx from 'clsx'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { SlotDetailModal } from '../calendar/SlotDetailModal'
import { GoalsBanner } from './GoalsBanner'
import { TrainingWeekCalendar } from './TrainingWeekCalendar'
import { TrainingMonthCalendar } from './TrainingMonthCalendar'
import { TrainingStatsSection } from './TrainingStatsSection'
import { TrainingFormModal, type InstantCompletion, type TrainingPrefill } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import { trainingCalendarApi, calendarApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AttachmentInput, CreatePersonalTraining, InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingCalendarSectionProps {
  api: TrainingCalendarAdapter
  // 'me' for the athlete's own tab, athleteId in the coach panel
  scopeKey: string
  // Coach view: completion read-only, different invalidations on mark-seen
  isCoachView?: boolean
}

// Copy/cut clipboard for week-view paste. Content is snapshotted (already decoded)
// at copy time, so pasting keeps working after the source scrolls out of the fetched
// range. Lives here (not in the week component) to survive week navigation; the coach
// panel remounts the whole section per athlete (key={athleteId}), which clears it —
// cross-athlete paste is impossible by construction.
interface TrainingClipboard {
  mode: 'copy' | 'cut'
  trainingId: string
  title: string
  description?: string
  durationMin: number
  // Carried so a copy→paste recreates the source's materials (cut/move keeps them via null)
  attachments: AttachmentInput[]
}

// Source attachments → API input (label decoded so the backend doesn't double-escape it).
// FILE attachments are re-referenced by their stored filename; a copy/duplicate shares the file.
function toAttachmentInputs(tr: PersonalTraining): AttachmentInput[] {
  return tr.attachments.map((a): AttachmentInput => {
    const label = a.label ? decodeHtmlEntities(a.label) : undefined
    return a.kind === 'FILE'
      ? {
          kind: 'FILE',
          filename: a.filename ?? undefined,
          originalName: a.fileName ?? undefined,
          mimeType: a.mimeType ?? undefined,
          sizeBytes: a.sizeBytes ?? undefined,
          label,
        }
      : { kind: 'LINK', url: a.url ?? undefined, label }
  })
}

function timeToMin(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

function minToTime(total: number): string {
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

export function TrainingCalendarSection({ api, scopeKey, isCoachView }: TrainingCalendarSectionProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  const view = searchParams.get('cal') === 'month' ? 'month' : 'week'
  const anchorParam = searchParams.get('calDate')
  const anchor = useMemo(() => {
    const d = anchorParam ? new Date(anchorParam) : new Date()
    return isNaN(d.getTime()) ? new Date() : d
  }, [anchorParam])

  const setView = (next: 'week' | 'month') => {
    const params = new URLSearchParams(searchParams)
    params.set('cal', next)
    setSearchParams(params, { replace: true })
  }

  const setAnchor = (date: Date) => {
    const params = new URLSearchParams(searchParams)
    params.set('calDate', format(date, 'yyyy-MM-dd'))
    setSearchParams(params, { replace: true })
  }

  // Range for the current view: week = Mon..Sun, month = 1st..last day
  const { from, to, weekStart } = useMemo(() => {
    if (view === 'week') {
      const start = startOfWeek(anchor, { weekStartsOn: 1 })
      return {
        from: format(start, 'yyyy-MM-dd'),
        to: format(addDays(start, 6), 'yyyy-MM-dd'),
        weekStart: format(start, 'yyyy-MM-dd'),
      }
    }
    return {
      from: format(startOfMonth(anchor), 'yyyy-MM-dd'),
      to: format(endOfMonth(anchor), 'yyyy-MM-dd'),
      weekStart: '',
    }
  }, [view, anchor])

  const rangeQuery = useQuery({
    queryKey: ['trainingCalendar', 'range', scopeKey, from, to],
    queryFn: () => api.getRange(from, to),
    // Coach comments/trainings show up without a manual refresh while the tab stays open
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Entering the calendar must always show the latest state (new trainings/comments) —
    // the global 5-min staleTime would otherwise serve a stale week from cache
    refetchOnMount: 'always',
  })

  // Opening the calendar counts as "read": clears the navbar/roster badges.
  // Unread dots on blocks deliberately stay for THIS visit (they show what's new),
  // but the range cache is marked stale WITHOUT an immediate refetch — global
  // staleTime is 5 min, so without this a return to the tab would serve cached
  // data and the dots would linger until a hard refresh.
  const seenMarked = useRef(false)
  useEffect(() => {
    // Wait for the fetch to settle: on an SPA return the cached (stale) data makes
    // isSuccess true instantly while a background refetch runs — firing mark-seen
    // then would update the read marker BEFORE the server computes the unread dots,
    // so the fresh response would arrive with every dot already cleared.
    if (!rangeQuery.isSuccess || rangeQuery.isFetching || seenMarked.current) return
    seenMarked.current = true
    api.markSeen().then(() => {
      queryClient.invalidateQueries({
        queryKey: ['trainingCalendar', 'range', scopeKey],
        refetchType: 'none',
      })
      if (isCoachView) {
        queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
        queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
      } else {
        queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'notifications'] })
      }
    }).catch(() => { seenMarked.current = false })
  }, [rangeQuery.isSuccess, rangeQuery.isFetching, api, isCoachView, queryClient, scopeKey])

  // ---------- modals ----------
  const [formOpen, setFormOpen] = useState(false)
  const [editedTraining, setEditedTraining] = useState<PersonalTraining | null>(null)
  const [prefillDate, setPrefillDate] = useState<string | undefined>(undefined)
  const [prefillTime, setPrefillTime] = useState<string | undefined>(undefined)
  // Duplicate flow: create-mode form seeded from an existing training
  const [duplicatePrefill, setDuplicatePrefill] = useState<TrainingPrefill | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [reservationHint, setReservationHint] = useState<ReservationOverlayItem | null>(null)
  // Full official-slot preview opened from the hint modal ("Zobacz szczegóły")
  const [officialSlotId, setOfficialSlotId] = useState<string | null>(null)

  const { data: officialSlot } = useQuery({
    queryKey: ['slot', officialSlotId],
    queryFn: () => calendarApi.getSlotDetails(officialSlotId!),
    enabled: !!officialSlotId,
    // Detail-by-id feeding a modal: never flash a previously opened slot
    placeholderData: undefined,
  })

  // Detail modal always shows fresh data from the range query
  const detailTraining = detailId
    ? rangeQuery.data?.trainings.find((tr) => tr.id === detailId) ?? null
    : null

  // ---------- mutations ----------
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'range', scopeKey] })
    // Completing/uncompleting/deleting changes the live-derived stats under the calendar
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'stats', scopeKey] })
    if (isCoachView) {
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
    }
  }

  const saveMutation = useMutation({
    mutationFn: async ({ data, completion }: { data: CreatePersonalTraining; completion?: InstantCompletion | null }) => {
      if (editedTraining) return api.updateTraining(editedTraining.id, data)
      const created = await api.createTraining(data)
      // Retroactive logging: create + immediately mark completed in one flow (athlete only)
      if (completion) return trainingCalendarApi.complete(created.id, completion)
      return created
    },
    onSuccess: () => {
      setFormOpen(false)
      setEditedTraining(null)
      invalidate()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (trainingId: string) => api.deleteTraining(trainingId),
    onSuccess: () => {
      setDetailId(null)
      invalidate()
    },
  })

  // Completion is athlete-only (the coach sees a read-only summary)
  const completeMutation = useMutation({
    mutationFn: ({ trainingId, data }: { trainingId: string; data: { feedback?: string; rpe?: number } }) =>
      trainingCalendarApi.complete(trainingId, data),
    onSuccess: invalidate,
  })

  const uncompleteMutation = useMutation({
    mutationFn: (trainingId: string) => trainingCalendarApi.uncomplete(trainingId),
    onSuccess: invalidate,
  })

  const openCreate = (date?: string, time?: string) => {
    setEditedTraining(null)
    setDuplicatePrefill(null)
    setPrefillDate(date)
    setPrefillTime(time)
    setFormOpen(true)
  }

  const openEdit = (training: PersonalTraining) => {
    setEditedTraining(training)
    setDuplicatePrefill(null)
    setFormOpen(true)
  }

  // Duplicate: create-mode form seeded with the source content, date defaults to +7 days
  // ("same training next week"); everything stays editable before saving
  const openDuplicate = (tr: PersonalTraining) => {
    setDetailId(null)
    setEditedTraining(null)
    setDuplicatePrefill({
      title: decodeHtmlEntities(tr.title),
      description: tr.description ? decodeHtmlEntities(tr.description) : undefined,
      startTime: tr.startTime.slice(0, 5),
      endTime: tr.endTime.slice(0, 5),
      attachments: toAttachmentInputs(tr),
    })
    setPrefillDate(format(addDays(new Date(tr.date), 7), 'yyyy-MM-dd'))
    setPrefillTime(undefined)
    setFormOpen(true)
  }

  // ---------- clipboard (copy/cut/paste) + drag&drop, week view only ----------
  const [clipboard, setClipboard] = useState<TrainingClipboard | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  // Escape cancels an armed clipboard (same interaction as the admin calendar)
  useEffect(() => {
    if (!clipboard) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setClipboard(null) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [clipboard])

  const armClipboard = (mode: 'copy' | 'cut') => (tr: PersonalTraining) => {
    setActionError(null)
    setClipboard({
      mode,
      trainingId: tr.id,
      title: decodeHtmlEntities(tr.title),
      description: tr.description ? decodeHtmlEntities(tr.description) : undefined,
      durationMin: timeToMin(tr.endTime.slice(0, 5)) - timeToMin(tr.startTime.slice(0, 5)),
      attachments: toAttachmentInputs(tr),
    })
  }

  const pasteMutation = useMutation({
    mutationFn: ({ clip, date, time }: { clip: TrainingClipboard; date: string; time: string }) => {
      const data: CreatePersonalTraining = {
        date,
        startTime: time,
        endTime: minToTime(Math.min(timeToMin(time) + clip.durationMin, 23 * 60 + 59)),
        title: clip.title,
        description: clip.description,
        // Copy recreates the materials; cut/move omits them so the backend keeps the originals
        attachments: clip.mode === 'copy' ? clip.attachments : undefined,
      }
      // Cut = move the existing training (keeps id, comments, completion);
      // copy = a fresh one, clipboard stays armed for repeated pastes
      return clip.mode === 'cut' ? api.updateTraining(clip.trainingId, data) : api.createTraining(data)
    },
    onSuccess: (_, { clip }) => {
      if (clip.mode === 'cut') setClipboard(null)
      invalidate()
    },
    onError: (err) => {
      // Source deleted meanwhile (or flag revoked) — disarm and say why
      setClipboard(null)
      setActionError(getErrorMessage(err))
    },
  })

  const handlePasteAt = (date: string, time: string) => {
    if (!clipboard || pasteMutation.isPending) return
    pasteMutation.mutate({ clip: clipboard, date, time })
  }

  const moveMutation = useMutation({
    mutationFn: ({ training, date, startTime, endTime }: {
      training: PersonalTraining; date: string; startTime: string; endTime: string
    }) =>
      // Title/description must round-trip decoded, or the server would re-escape entities
      api.updateTraining(training.id, {
        date,
        startTime,
        endTime,
        title: decodeHtmlEntities(training.title),
        description: training.description ? decodeHtmlEntities(training.description) : undefined,
      }),
    onSuccess: () => {
      setActionError(null)
      invalidate()
    },
    onError: (err) => setActionError(getErrorMessage(err)),
  })

  const handleTrainingMove = (trainingId: string, date: string, startTime: string, endTime: string) => {
    const tr = rangeQuery.data?.trainings.find((x) => x.id === trainingId)
    if (!tr || moveMutation.isPending) return
    moveMutation.mutate({ training: tr, date, startTime, endTime })
  }

  // ---------- render ----------
  if (rangeQuery.isLoading) {
    return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  }
  if (rangeQuery.isError) {
    return <QueryError error={rangeQuery.error} onRetry={() => rangeQuery.refetch()} />
  }

  const trainings = rangeQuery.data?.trainings ?? []
  const reservations = rangeQuery.data?.reservations ?? []
  const invitations = rangeQuery.data?.invitations ?? []
  const deletions = rangeQuery.data?.deletions ?? []

  // A held seat is booked in the PUBLIC calendar — deep-link straight into the
  // slot/event modal there, so "click the amber block" ends in an actual booking.
  // Coach view: carry a returnTo so closing that modal comes back to this athlete's
  // calendar instead of stranding the admin on the public one (CalendarPage reads it).
  const openInvitation = (inv: InvitationOverlayItem) => {
    const target = inv.slotId ? `slot=${inv.slotId}` : `event=${inv.eventId}`
    const returnTo = isCoachView ? location.pathname + location.search : undefined
    navigate(`/calendar?date=${inv.date}&${target}`, returnTo ? { state: { returnTo } } : undefined)
  }

  return (
    <div className="space-y-4">
      {/* Personal goals (short/medium/long-term) + trophy chest — coach edits, athlete reads */}
      <GoalsBanner api={api} scopeKey={scopeKey} isCoachView={isCoachView} />

      {/* Future trainings removed by the other side since the last visit —
          without this, the badge would point at a calendar with nothing visibly new */}
      {deletions.length > 0 && (
        <div className="p-3 bg-rose-500/5 border border-rose-500/20 rounded-lg">
          <div className="flex items-center gap-2 text-sm font-medium text-rose-300/90 mb-1.5">
            <Trash2 className="w-4 h-4" />
            {isCoachView ? t('deletions.titleCoach') : t('deletions.titleAthlete')}
          </div>
          <ul className="space-y-0.5">
            {deletions.map((d, i) => (
              <li key={i} className="text-sm text-surface-300">
                {format(new Date(d.date), 'dd.MM.yyyy')}{' '}
                {d.startTime.slice(0, 5)} - {d.endTime.slice(0, 5)} —{' '}
                <span className="font-medium">{decodeHtmlEntities(d.title)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {/* Armed clipboard: instruction banner (amber = cut/move, primary = copy) */}
      {clipboard && (
        <div
          className={clsx(
            'flex items-center justify-between gap-3 p-3 border rounded-lg',
            clipboard.mode === 'cut'
              ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
              : 'bg-primary-500/10 border-primary-500/30 text-primary-300',
          )}
        >
          <span className="text-sm">
            {t(clipboard.mode === 'cut' ? 'clipboard.cutBanner' : 'clipboard.copiedBanner', { title: clipboard.title })}
          </span>
          <button
            onClick={() => setClipboard(null)}
            className="p-1 rounded hover:bg-surface-800 transition-colors shrink-0"
            title={t('clipboard.cancel')}
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Rejected move/paste (e.g. source deleted meanwhile) */}
      {actionError && (
        <div className="flex items-center justify-between gap-3 p-3 bg-rose-500/10 border border-rose-500/30 rounded-lg">
          <span className="text-sm text-rose-300">{actionError}</span>
          <button
            onClick={() => setActionError(null)}
            className="p-1 rounded hover:bg-surface-800 transition-colors shrink-0 text-rose-300"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Toolbar: view toggle + add button */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex rounded-lg border border-surface-700 overflow-hidden">
          {(['month', 'week'] as const).map((v) => (
            <button
              key={v}
              onClick={() => setView(v)}
              className={clsx(
                'px-4 py-1.5 text-sm font-medium transition-colors',
                view === v
                  ? 'bg-primary-600 text-white'
                  : 'bg-surface-900 text-surface-400 hover:text-surface-200',
              )}
            >
              {t(`views.${v}`)}
            </button>
          ))}
        </div>

        <button
          onClick={() => openCreate()}
          className="flex items-center gap-1.5 px-4 py-1.5 text-sm font-medium bg-primary-600 hover:bg-primary-500 text-white rounded-lg transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('addTraining')}
        </button>
      </div>

      {view === 'week' ? (
        <TrainingWeekCalendar
          startDate={weekStart}
          trainings={trainings}
          reservations={reservations}
          invitations={invitations}
          invitationLabel={t('overlay.invitation')}
          onPrevWeek={() => setAnchor(addDays(startOfWeek(anchor, { weekStartsOn: 1 }), -7))}
          onNextWeek={() => setAnchor(addDays(startOfWeek(anchor, { weekStartsOn: 1 }), 7))}
          onToday={() => setAnchor(new Date())}
          onTrainingClick={(tr) => setDetailId(tr.id)}
          onReservationClick={setReservationHint}
          onInvitationClick={openInvitation}
          onDayClick={openCreate}
          onTrainingMove={handleTrainingMove}
          onTrainingCopy={armClipboard('copy')}
          onTrainingCut={armClipboard('cut')}
          cutTrainingId={clipboard?.mode === 'cut' ? clipboard.trainingId : null}
          copiedTrainingId={clipboard?.mode === 'copy' ? clipboard.trainingId : null}
          pasteActive={!!clipboard}
          onPasteAt={handlePasteAt}
        />
      ) : (
        <TrainingMonthCalendar
          currentMonth={anchor}
          onMonthChange={setAnchor}
          trainings={trainings}
          reservations={reservations}
          invitations={invitations}
          invitationLabel={t('overlay.invitation')}
          onTrainingClick={(tr) => setDetailId(tr.id)}
          onReservationClick={setReservationHint}
          onInvitationClick={openInvitation}
          onDayClick={openCreate}
        />
      )}

      {trainings.length === 0 && reservations.length === 0 && (
        <div className="text-center py-6">
          <p className="text-surface-400 font-medium">{t('empty.title')}</p>
          <p className="text-sm text-surface-500 mt-1">{t('empty.hint')}</p>
        </div>
      )}

      {/* Live-derived statistics over completed trainings + attended reservations */}
      <TrainingStatsSection api={api} scopeKey={scopeKey} />

      {/* Add / edit */}
      <TrainingFormModal
        isOpen={formOpen}
        onClose={() => { setFormOpen(false); setEditedTraining(null); setDuplicatePrefill(null); saveMutation.reset() }}
        training={editedTraining}
        initialDate={prefillDate}
        initialTime={prefillTime}
        prefill={duplicatePrefill}
        onSubmit={(data, completion) => saveMutation.mutate({ data, completion })}
        saving={saveMutation.isPending}
        allowInstantComplete={!isCoachView}
        onUpload={api.uploadAttachment}
        submitError={saveMutation.isError ? getErrorMessage(saveMutation.error) : null}
      />

      {/* Detail: completion + comment thread + edit/delete */}
      <TrainingDetailModal
        training={detailTraining}
        onClose={() => setDetailId(null)}
        api={api}
        isCoachView={isCoachView}
        onEdit={(tr) => { setDetailId(null); openEdit(tr) }}
        onDuplicate={openDuplicate}
        onDelete={(tr) => deleteMutation.mutate(tr.id)}
        onComplete={(tr, data) => completeMutation.mutate({ trainingId: tr.id, data })}
        onUncomplete={(tr) => uncompleteMutation.mutate(tr.id)}
        mutating={completeMutation.isPending || uncompleteMutation.isPending || deleteMutation.isPending}
        errorMessage={
          deleteMutation.isError ? getErrorMessage(deleteMutation.error)
            : completeMutation.isError ? getErrorMessage(completeMutation.error)
            : uncompleteMutation.isError ? getErrorMessage(uncompleteMutation.error)
            : null
        }
        onCommentPosted={isCoachView
          ? () => queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
          : undefined}
      />

      {/* Read-only reservation hint + gateway to the full official-slot preview */}
      <Modal
        isOpen={reservationHint !== null}
        onClose={() => setReservationHint(null)}
        title={reservationHint?.title || t('overlay.reservation')}
      >
        <div className="flex items-start gap-3">
          <Lock className="w-5 h-5 text-surface-400 shrink-0 mt-0.5" />
          <div>
            {reservationHint && (
              <p className="text-sm text-surface-300 mb-2">
                {format(new Date(reservationHint.date), 'dd.MM.yyyy')}{' '}
                {reservationHint.startTime.slice(0, 5)} - {reservationHint.endTime.slice(0, 5)}
              </p>
            )}
            <p className="text-sm text-surface-400">{t('overlay.readonlyHint')}</p>
          </div>
        </div>
        <div className="flex justify-end mt-4">
          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setOfficialSlotId(reservationHint!.slotId)
              setReservationHint(null)
            }}
          >
            {t('overlay.viewDetails')}
          </Button>
        </div>
      </Modal>

      {/* Full official-slot details in place — no trip to the main calendar needed */}
      <SlotDetailModal
        slot={officialSlot ?? null}
        isOpen={!!officialSlotId}
        onClose={() => {
          setOfficialSlotId(null)
          // Cancelling/updating the booking inside the modal changes the overlay and the stats
          queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'range', scopeKey] })
          queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'stats', scopeKey] })
          queryClient.invalidateQueries({ queryKey: ['reservations'] })
          queryClient.invalidateQueries({ queryKey: ['calendar'] })
        }}
      />
    </div>
  )
}
