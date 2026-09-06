import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Plus, Lock, Trash2, X } from 'lucide-react'
import { format, addDays } from 'date-fns'
import clsx from 'clsx'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { QueryError } from '../ui/QueryError'
import { SlotDetailModal } from '../calendar/SlotDetailModal'
import { useNoteMarks } from '../admin/useNoteMarks'
import { GoalsBanner } from './GoalsBanner'
import { WeightPanel } from './WeightPanel'
import { ReservationRatingSection } from './ReservationRatingSection'
import { TrainingWeekCalendar } from './TrainingWeekCalendar'
import { TrainingMonthCalendar } from './TrainingMonthCalendar'
import { TrainingMonthDots } from './TrainingMonthDots'
import { TrainingDaySheet } from './TrainingDaySheet'
import { TrainingStatsSection } from './TrainingStatsSection'
import { TrainingFormModal, type InstantCompletion, type TrainingPrefill } from './TrainingFormModal'
import { TrainingDetailModal } from './TrainingDetailModal'
import { CommentThread } from './CommentThread'
import { SaveAsTemplateModal } from './SaveAsTemplateModal'
import type { TemplateDraft } from './TrainingTemplateForm'
import { monthGridRange, resolveInitialView, stepWeek, weekRange } from './monthGrid'
import { useCompactViewport } from '../../hooks/useCompactViewport'
import { useChildDirty } from '../../hooks/useChildDirty'
import { trainingCalendarApi, calendarApi } from '../../api/client'
import { useTrainingClipboard, type TrainingClipboardEntry } from '../../context/TrainingClipboardContext'
import { getErrorMessage } from '../../utils/errors'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { keepWithinEntity } from '../../utils/queryEntity'
import { nowInWarsaw, parseCalendarDate } from '../../utils/calendarDate'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AttachmentInput, CreatePersonalTraining, InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingCalendarSectionProps {
  api: TrainingCalendarAdapter
  // 'me' for the athlete's own tab, athleteId in the coach panel
  scopeKey: string
  // Whose calendar this is, for the clipboard banner after a copy travels here from
  // somewhere else. Omitted on the athlete's own tab.
  scopeLabel?: string
  // Coach view: completion read-only, different invalidations on mark-seen
  isCoachView?: boolean
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

export function TrainingCalendarSection({ api, scopeKey, scopeLabel, isCoachView }: TrainingCalendarSectionProps) {
  const { t } = useTranslation('training')
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()

  // Reactive: decides WHICH month component renders, so it must follow a resize.
  const compactViewport = useCompactViewport()
  // Frozen at first render: the DEFAULT view must not follow a resize. Reading the live
  // value here would flip the calendar under someone rotating their phone. Keep these two
  // reads apart — collapsing them into one restores exactly that bug. (State rather than a
  // ref because this IS read during render, and a ref read there is a lint error.)
  const [initialCompact] = useState(compactViewport)
  const view = resolveInitialView(searchParams.get('cal'), initialCompact)
  const anchorParam = searchParams.get('calDate')
  // The anchor round-trips through the URL as a 'yyyy-MM-dd' label: written with format()
  // (local) and read back here. Reading it with `new Date()` parsed it as UTC midnight, so
  // west of Greenwich it came back a day early — startOfWeek then snapped to the PREVIOUS
  // Monday and "next week" wrote back the date already in the URL. Same string in, same
  // string out, nothing re-rendered: the arrow was dead. See utils/calendarDate.ts.
  const anchor = useMemo(() => {
    const d = anchorParam ? parseCalendarDate(anchorParam) : nowInWarsaw()
    return isNaN(d.getTime()) ? nowInWarsaw() : d
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

  // Range for the current view: week = Mon..Sun, month = the whole 42-day grid including
  // the greyed padding days (42 < the backend's 62-day cap)
  const { from, to, weekStart } = useMemo(() => {
    if (view === 'week') return weekRange(anchor)
    return { ...monthGridRange(anchor), weekStart: '' }
  }, [view, anchor])

  const rangeKey = useMemo(
    () => ['trainingCalendar', 'range', scopeKey, from, to] as const,
    [scopeKey, from, to],
  )

  const rangeQuery = useQuery({
    queryKey: rangeKey,
    queryFn: () => api.getRange(from, to),
    // Coach comments/trainings show up without a manual refresh while the tab stays open
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Entering the calendar must always show the latest state (new trainings/comments) —
    // the global 5-min staleTime would otherwise serve a stale week from cache
    refetchOnMount: 'always',
    // Paging months should keep the grid on screen instead of collapsing it to a spinner,
    // but the global keepPreviousData would also keep it across a change of athlete. Fence
    // it to the trailing from/to: same person, different page.
    placeholderData: (previous, previousQuery) =>
      keepWithinEntity(previous, previousQuery, rangeKey, 2),
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
  // "Save as template" flow: library form seeded from an existing entry (coach only)
  const [templateDraft, setTemplateDraft] = useState<TemplateDraft | null>(null)
  const [detailId, setDetailId] = useState<string | null>(null)
  const [reservationHint, setReservationHint] = useState<ReservationOverlayItem | null>(null)
  // What the thread in that modal would lose on Escape: a typed message, a staged file, an open
  // correction. Read at click time rather than from a prop captured a render earlier — see
  // useChildDirty for why Modal takes a getter.
  const [reservationThreadDirty, reportReservationThreadDirty] = useChildDirty(reservationHint !== null)
  // Full official-slot preview opened from the hint modal ("Zobacz szczegóły")
  const [officialSlotId, setOfficialSlotId] = useState<string | null>(null)
  // One day's entries: the phone's only way into a day, and the desktop's way past "+N"
  const [daySheetDate, setDaySheetDate] = useState<string | null>(null)

  const { data: officialSlot } = useQuery({
    queryKey: ['slot', officialSlotId],
    queryFn: () => calendarApi.getSlotDetails(officialSlotId!),
    enabled: !!officialSlotId,
    // Detail-by-id feeding a modal: never flash a previously opened slot
    placeholderData: undefined,
  })

  /**
   * Detail modal always shows fresh data from the range query — but it must not VANISH when the
   * entry leaves that range.
   *
   * The calendar refetches in the background every 60s, and the plan is shared: the other side can
   * move the entry into another week or delete it while the card is open. Reading straight from the
   * range then turned the card null and closed it mid-sentence, taking a half-written message with
   * it. Now the last version seen is kept and the card says the entry is gone, which is also the
   * only way the unsaved-work guard can do its job — a modal that unmounts asks nobody anything.
   */
  const [lastSeenDetail, setLastSeenDetail] = useState<PersonalTraining | null>(null)
  const liveDetail = detailId
    ? rangeQuery.data?.trainings.find((tr) => tr.id === detailId) ?? null
    : null
  // Adjusted during render, the supported pattern for deriving state from changing input (the same
  // one Modal uses for its pending confirmation). State rather than a ref because this IS read in
  // render, and each branch settles after one pass: the query hands back a stable object until it
  // refetches, and closing the card clears the memory exactly once.
  if (liveDetail && liveDetail !== lastSeenDetail) setLastSeenDetail(liveDetail)
  if (!detailId && lastSeenDetail) setLastSeenDetail(null)
  const detailTraining = liveDetail ?? lastSeenDetail
  // Tells "the range is still loading" apart from "the other side removed it": only claim it is
  // gone once there is data in hand and the entry is not in it.
  const detailVanished = !!detailId && !liveDetail && !!rangeQuery.data && !!lastSeenDetail

  // ---------- mutations ----------
  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'range', scopeKey] })
    // Completing/uncompleting/deleting changes the live-derived stats under the calendar
    queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'stats', scopeKey] })
    if (isCoachView) {
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
    }
  }

  /**
   * These four report their failures inline (`submitError` on the form, `errorMessage` on the
   * detail modal), so each declares an `onError` even though it does nothing: main.tsx only fires
   * the global error toast for mutations that have NONE, and its whole contract is that a
   * component already showing the failure owns the messaging. Without these, every failed save,
   * completion or delete was announced twice — once as a toast and once in red next to the button.
   */
  const reportedInline = () => {}

  /**
   * The id of an entry this flow already created, kept so a retry does not create a second one.
   *
   * "Log it as done right away" is two calls, and only the first is idempotent-ish: when the
   * create landed and the completion did not (a dropped connection, a 409 on the "must have
   * started" boundary), the form stayed open with the error — and pressing Save again made
   * ANOTHER training. Now the retry only completes what already exists.
   */
  const pendingCompletionId = useRef<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: async ({ data, completion }: { data: CreatePersonalTraining; completion?: InstantCompletion | null }) => {
      if (editedTraining) return api.updateTraining(editedTraining.id, data)

      // A retry after the completion half failed: the entry is already there, only the tick is not
      const existingId = pendingCompletionId.current
      const created = existingId ? null : await api.createTraining(data)
      const trainingId = existingId ?? created!.id

      // Retroactive logging: create + immediately mark completed in one flow (athlete only)
      if (!completion) {
        pendingCompletionId.current = null
        // Unticked on the retry — the entry exists, so this is an edit, not a second create
        return created ?? (await api.updateTraining(trainingId, data))
      }
      // Remembered BEFORE the second call, so a failure there leaves the id behind for the retry
      pendingCompletionId.current = trainingId
      const done = await trainingCalendarApi.complete(trainingId, completion)
      pendingCompletionId.current = null
      return done
    },
    onSuccess: () => {
      setFormOpen(false)
      setEditedTraining(null)
      invalidate()
    },
    onError: reportedInline,
  })

  const deleteMutation = useMutation({
    mutationFn: (trainingId: string) => api.deleteTraining(trainingId),
    onSuccess: () => {
      setDetailId(null)
      invalidate()
    },
    onError: reportedInline,
  })

  // Completion is athlete-only (the coach sees a read-only summary)
  const completeMutation = useMutation({
    mutationFn: ({ trainingId, data }: { trainingId: string; data: { feedback?: string; rpe?: number } }) =>
      trainingCalendarApi.complete(trainingId, data),
    onSuccess: invalidate,
    onError: reportedInline,
  })

  const uncompleteMutation = useMutation({
    mutationFn: (trainingId: string) => trainingCalendarApi.uncomplete(trainingId),
    onSuccess: invalidate,
    onError: reportedInline,
  })

  // Every fresh open starts a new entry, so the half-finished one from a previous attempt must not
  // be adopted by it — the id only survives a RETRY of the same save.
  const openCreate = (date?: string, time?: string) => {
    pendingCompletionId.current = null
    setEditedTraining(null)
    setDuplicatePrefill(null)
    setPrefillDate(date)
    setPrefillTime(time)
    setFormOpen(true)
  }

  const openEdit = (training: PersonalTraining) => {
    pendingCompletionId.current = null
    setEditedTraining(training)
    setDuplicatePrefill(null)
    setFormOpen(true)
  }

  // "Save as template": the library form seeded from this entry. A task keeps its ceiling and
  // brings no duration — the one entry that repeats verbatim across every athlete ("max 2200
  // kcal") is exactly the one worth having in the library.
  const openSaveAsTemplate = (tr: PersonalTraining) => {
    const timed = !!(tr.startTime && tr.endTime)
    setTemplateDraft({
      kind: tr.kind,
      title: decodeHtmlEntities(tr.title),
      description: tr.description ? decodeHtmlEntities(tr.description) : undefined,
      defaultDurationMinutes: tr.kind === 'TASK'
        ? null
        : (timed ? timeToMin(tr.endTime!.slice(0, 5)) - timeToMin(tr.startTime!.slice(0, 5)) : null),
      targetCalories: tr.targetCalories,
      attachments: toAttachmentInputs(tr),
    })
  }

  // Duplicate: create-mode form seeded with the source content, date defaults to +7 days
  // ("same training next week"); everything stays editable before saving
  const openDuplicate = (tr: PersonalTraining) => {
    pendingCompletionId.current = null
    setDetailId(null)
    setEditedTraining(null)
    setDuplicatePrefill({
      kind: tr.kind,
      title: decodeHtmlEntities(tr.title),
      description: tr.description ? decodeHtmlEntities(tr.description) : undefined,
      // Untimed source duplicates as untimed (null times → form defaults to all-day)
      startTime: tr.startTime ? tr.startTime.slice(0, 5) : null,
      endTime: tr.endTime ? tr.endTime.slice(0, 5) : null,
      attachments: toAttachmentInputs(tr),
    })
    setPrefillDate(format(addDays(parseCalendarDate(tr.date), 7), 'yyyy-MM-dd'))
    setPrefillTime(undefined)
    setFormOpen(true)
  }

  // ---------- clipboard (copy/cut/paste) + drag&drop, week view only ----------
  // State lives above the router (TrainingClipboardProvider) so a copy survives the remount
  // the coach panel does per athlete — that survival IS cross-athlete paste.
  const { clipboard, setClipboard } = useTrainingClipboard()
  const [actionError, setActionError] = useState<string | null>(null)

  /**
   * The clipboard as THIS calendar sees it.
   *
   * A copy travels between athletes — that is the point of hoisting the state. A cut does not:
   * it is a MOVE of an existing row, and moving one athlete's training into another's would drag
   * its comment thread and its completion ("legs were dead today") into somebody else's plan.
   * Deriving it in one place rather than checking the scope at each use means there is no render
   * in which a foreign cut still looks armed.
   */
  const armed = clipboard && (clipboard.mode === 'copy' || clipboard.scopeKey === scopeKey)
    ? clipboard
    : null
  const fromAnotherCalendar = !!armed && armed.scopeKey !== scopeKey

  // ...and the foreign cut is dropped rather than parked: walking back to its own calendar and
  // finding a cut you had given up on silently re-armed is worse than losing it on the way out.
  useEffect(() => {
    if (clipboard && clipboard.mode === 'cut' && clipboard.scopeKey !== scopeKey) setClipboard(null)
  }, [clipboard, scopeKey, setClipboard])

  // Escape cancels an armed clipboard (same interaction as the admin calendar)
  useEffect(() => {
    if (!armed) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setClipboard(null) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [armed, setClipboard])

  const armClipboard = (mode: 'copy' | 'cut') => (tr: PersonalTraining) => {
    setActionError(null)
    const timed = !!(tr.startTime && tr.endTime)
    setClipboard({
      mode,
      scopeKey,
      scopeLabel,
      trainingId: tr.id,
      kind: tr.kind,
      targetCalories: tr.targetCalories,
      title: decodeHtmlEntities(tr.title),
      description: tr.description ? decodeHtmlEntities(tr.description) : undefined,
      startTime: timed ? tr.startTime!.slice(0, 5) : null,
      durationMin: timed
        ? timeToMin(tr.endTime!.slice(0, 5)) - timeToMin(tr.startTime!.slice(0, 5))
        : 90,
      attachments: toAttachmentInputs(tr),
    })
  }

  const pasteMutation = useMutation({
    mutationFn: ({ clip, date, time }: { clip: TrainingClipboardEntry; date: string; time: string | null }) => {
      const data: CreatePersonalTraining = {
        // Ignored by the server on a cut (which is an update); decisive on a copy
        kind: clip.kind,
        targetCalories: clip.targetCalories,
        date,
        // Both omitted = untimed, which is a legal training here and the default case
        startTime: time ?? undefined,
        endTime: time
          ? minToTime(Math.min(timeToMin(time) + clip.durationMin, 23 * 60 + 59))
          : undefined,
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

  /**
   * Three callers, three meanings for the hour:
   * - the week grid passes one (drop onto 17:00),
   * - the week's all-day lane passes an explicit null (drop into "no hour"),
   * - the month and dot grids pass nothing, and the paste keeps the source's own hour.
   *
   * A task overrides all of it: it holds for the whole day, so an hour is meaningless for one
   * and the API rejects it outright. Without this, copying a task from the day sheet and
   * pasting it on the week grid answered with a red error strip.
   */
  const handlePasteAt = (date: string, time?: string | null) => {
    if (!armed || pasteMutation.isPending) return
    const at = armed.kind === 'TASK' ? null : (time !== undefined ? time : armed.startTime)
    pasteMutation.mutate({ clip: armed, date, time: at })
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
        // Re-sent for the same reason as the title: a PUT REPLACES it, so omitting it clears a
        // task's ceiling. (Only `attachments` has leave-untouched semantics — an Integer has no
        // empty value to distinguish "unchanged" from "cleared".)
        targetCalories: training.targetCalories,
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

  // Coach only, and the same range the grid is showing. Stamped onto the entries rather than
  // passed down: TrainingBlock is rendered by four different hosts (week, month, dots, day
  // sheet), and threading a Set through all four to reach a 12px icon is more plumbing than
  // the signal is worth. Both hooks sit ABOVE the loading/error returns below — a hook after
  // an early return changes the hook order between renders.
  const noteMarks = useNoteMarks(!!isCoachView, from, to)

  const trainings = useMemo(() => {
    const list = rangeQuery.data?.trainings ?? []
    if (!isCoachView || noteMarks.trainings.size === 0) return list
    return list.map((tr) =>
      noteMarks.trainings.has(tr.id) ? { ...tr, hasPrivateNote: true } : tr)
  }, [rangeQuery.data?.trainings, isCoachView, noteMarks])

  // ---------- render ----------
  if (rangeQuery.isLoading) {
    return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  }
  if (rangeQuery.isError) {
    return <QueryError error={rangeQuery.error} onRetry={() => rangeQuery.refetch()} />
  }

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
                {format(parseCalendarDate(d.date), 'dd.MM.yyyy')}{' '}
                {d.startTime && d.endTime ? `${d.startTime.slice(0, 5)} - ${d.endTime.slice(0, 5)} — ` : '— '}
                <span className="font-medium">{decodeHtmlEntities(d.title)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {/* Armed clipboard: instruction banner (amber = cut/move, primary = copy) */}
      {armed && (
        <div
          className={clsx(
            'flex items-center justify-between gap-3 p-3 border rounded-lg',
            armed.mode === 'cut'
              ? 'bg-amber-500/10 border-amber-500/30 text-amber-300'
              : 'bg-primary-500/10 border-primary-500/30 text-primary-300',
          )}
        >
          <span className="text-sm">
            {t(armed.mode === 'cut' ? 'clipboard.cutBanner' : 'clipboard.copiedBanner', { title: armed.title })}
            {/* Arrived from another calendar: say whose, or a paste into the wrong person's plan
                looks exactly like a paste into the right one */}
            {fromAnotherCalendar && (
              <span className="block text-xs opacity-80">
                {armed.scopeLabel
                  ? t('clipboard.fromAthlete', { name: armed.scopeLabel })
                  : t('clipboard.fromOwnCalendar')}
              </span>
            )}
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
          onPrevWeek={() => setAnchor(stepWeek(anchor, -1))}
          onNextWeek={() => setAnchor(stepWeek(anchor, 1))}
          onToday={() => setAnchor(nowInWarsaw())}
          onTrainingClick={(tr) => setDetailId(tr.id)}
          onReservationClick={setReservationHint}
          onInvitationClick={openInvitation}
          onDayClick={openCreate}
          onTrainingMove={handleTrainingMove}
          onTrainingCopy={armClipboard('copy')}
          onTrainingCut={armClipboard('cut')}
          cutTrainingId={armed?.mode === 'cut' ? armed.trainingId : null}
          copiedTrainingId={armed?.mode === 'copy' ? armed.trainingId : null}
          pasteActive={!!armed}
          onPasteAt={handlePasteAt}
          isCoachView={isCoachView}
        />
      ) : compactViewport ? (
        /* Below sm a day cell is ~45px: it can say that something is planned, never what.
           A different component with different interactions, not a restyle — rendering
           both trees and hiding one would leave the hidden half on the keyboard path. */
        <TrainingMonthDots
          currentMonth={anchor}
          onMonthChange={setAnchor}
          trainings={trainings}
          reservations={reservations}
          invitations={invitations}
          onDayExpand={setDaySheetDate}
          pasteActive={!!armed}
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
          onDayExpand={setDaySheetDate}
          pasteActive={!!armed}
          onPasteAt={handlePasteAt}
          cutTrainingId={armed?.mode === 'cut' ? armed.trainingId : null}
          copiedTrainingId={armed?.mode === 'copy' ? armed.trainingId : null}
          isCoachView={isCoachView}
        />
      )}

      {daySheetDate && (
        <TrainingDaySheet
          date={daySheetDate}
          trainings={trainings.filter((tr) => tr.date === daySheetDate)}
          reservations={reservations.filter((r) => r.date === daySheetDate)}
          invitations={invitations.filter((inv) => inv.date === daySheetDate)}
          invitationLabel={t('overlay.invitation')}
          onClose={() => setDaySheetDate(null)}
          onTrainingClick={(tr) => setDetailId(tr.id)}
          onReservationClick={setReservationHint}
          onInvitationClick={openInvitation}
          onAdd={openCreate}
          onTrainingCopy={armClipboard('copy')}
          onTrainingCut={armClipboard('cut')}
          isCoachView={isCoachView}
        />
      )}

      {/* Invitations count as something planned. Without them a week holding only a held seat drew
          the loud amber "book me!" block and put "nothing planned" directly underneath it. */}
      {trainings.length === 0 && reservations.length === 0 && invitations.length === 0 && (
        <div className="text-center py-6">
          <p className="text-surface-400 font-medium">{t('empty.title')}</p>
          <p className="text-sm text-surface-500 mt-1">{t('empty.hint')}</p>
        </div>
      )}

      {/* Weight sits below the calendar with the other numbers: the plan is what both sides open
          this tab for, and the daily weigh-in is a quick detour on the way to the statistics —
          not something that should push the week down the screen. The weight-goal cards stay up
          in the banner with the rest of the goals. */}
      <WeightPanel api={api} scopeKey={scopeKey} isCoachView={isCoachView} />

      {/* Live-derived statistics over completed trainings + attended reservations */}
      <TrainingStatsSection api={api} scopeKey={scopeKey} isCoachView={isCoachView} />

      {/* Add / edit */}
      <TrainingFormModal
        isOpen={formOpen}
        onClose={() => {
          setFormOpen(false)
          setEditedTraining(null)
          setDuplicatePrefill(null)
          // Walking away abandons the half-finished entry; the next create must not adopt its id
          pendingCompletionId.current = null
          saveMutation.reset()
        }}
        training={editedTraining}
        initialDate={prefillDate}
        initialTime={prefillTime}
        prefill={duplicatePrefill}
        onSubmit={(data, completion) => saveMutation.mutate({ data, completion })}
        saving={saveMutation.isPending}
        allowInstantComplete={!isCoachView}
        onUpload={api.uploadAttachment}
        templatesEnabled={isCoachView}
        submitError={saveMutation.isError ? getErrorMessage(saveMutation.error) : null}
      />

      {/* Detail: completion + comment thread + edit/delete */}
      <TrainingDetailModal
        training={detailTraining}
        vanished={detailVanished}
        onClose={() => setDetailId(null)}
        api={api}
        isCoachView={isCoachView}
        onEdit={(tr) => { setDetailId(null); openEdit(tr) }}
        onDuplicate={openDuplicate}
        // The library belongs to the coach; the athlete never sees templates at all
        onSaveAsTemplate={isCoachView ? openSaveAsTemplate : undefined}
        onDelete={(tr) => deleteMutation.mutate(tr.id)}
        onComplete={(tr, data) => completeMutation.mutateAsync({ trainingId: tr.id, data })}
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

      {/* Library form seeded from the entry above — stays open over the detail modal it came from */}
      <SaveAsTemplateModal draft={templateDraft} onClose={() => setTemplateDraft(null)} />

      {/* The booking itself stays read-only here — but the conversation about it does not. */}
      <Modal
        isOpen={reservationHint !== null}
        onClose={() => setReservationHint(null)}
        title={reservationHint?.title || t('overlay.reservation')}
        size="lg"
        confirmClose={reservationThreadDirty}
      >
        <div className="flex items-start gap-3">
          <Lock className="w-5 h-5 text-surface-400 shrink-0 mt-0.5" />
          <div>
            {reservationHint && (
              <p className="text-sm text-surface-300 mb-2">
                {format(parseCalendarDate(reservationHint.date), 'dd.MM.yyyy')}{' '}
                {reservationHint.startTime.slice(0, 5)} - {reservationHint.endTime.slice(0, 5)}
              </p>
            )}
            <p className="text-sm text-surface-400">{t('overlay.readonlyHint')}</p>
          </div>
        </div>

        {/* Athlete rates an attended booking; the coach only reads it */}
        {reservationHint && !isCoachView && reservationHint.canRate && (
          <ReservationRatingSection
            key={reservationHint.id}
            reservation={reservationHint}
            onRated={() => {
              queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'range', scopeKey] })
              queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'stats', scopeKey] })
              // Deliberately NOT closing the modal: the thread underneath may hold a half-typed
              // reply, and rating is no longer the last thing there is to do here.
            }}
          />
        )}

        {/* Same conversation as under a plan entry. A multi-day course shares one thread, so the
            note above the composer says which days it covers — otherwise the second day looks like
            a thread that mysteriously already has messages in it. */}
        {reservationHint && (
          <div className="mt-4 pt-4 border-t border-surface-700">
            {reservationHint.eventId && (
              <p className="text-xs text-surface-400 mb-2">{t('overlay.threadCoversEvent')}</p>
            )}
            <CommentThread
              key={reservationHint.id}
              target={{ kind: 'reservation', id: reservationHint.id }}
              api={api}
              onPosted={() => {
                // The dot on the tile is computed server-side from this thread
                queryClient.invalidateQueries({ queryKey: ['trainingCalendar', 'range', scopeKey] })
                if (isCoachView) {
                  queryClient.invalidateQueries({ queryKey: ['admin', 'trainingCalendar', 'athletes'] })
                }
              }}
              onDirtyChange={reportReservationThreadDirty}
            />
          </div>
        )}

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
