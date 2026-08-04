import { useEffect, useMemo, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, isToday, addDays } from 'date-fns'
import clsx from 'clsx'
import { TrainingBlock, ReservationBlock, InvitationBlock } from './TrainingBlock'
import { useSlotDrag } from '../../hooks/useSlotDrag'
import { useDateLocale } from '../../utils/dateFnsLocale'
import {
  HOUR_HEIGHT, START_HOUR, TOTAL_HOURS,
  clickToTime, splitDay,
  type DayEntries,
} from './weekLayout'
import type { InvitationOverlayItem, PersonalTraining, ReservationOverlayItem } from '../../types'

interface TrainingWeekCalendarProps {
  // Monday of the displayed week, yyyy-MM-dd
  startDate: string
  trainings: PersonalTraining[]
  reservations: ReservationOverlayItem[]
  invitations: InvitationOverlayItem[]
  invitationLabel: string
  onPrevWeek: () => void
  onNextWeek: () => void
  onToday: () => void
  onTrainingClick: (training: PersonalTraining) => void
  onReservationClick: (reservation: ReservationOverlayItem) => void
  onInvitationClick: (invitation: InvitationOverlayItem) => void
  // Click on an empty area of a day column -> add-training prefilled with that date
  // and the clicked hour (snapped to 30 min)
  onDayClick: (date: string, time?: string) => void
  // Drag&drop / resize: PUT with the new date+times (id keeps comments & completion)
  onTrainingMove?: (trainingId: string, date: string, startTime: string, endTime: string) => void
  // Clipboard (copy/cut/paste) — state lives in TrainingCalendarSection so it survives week navigation
  onTrainingCopy?: (training: PersonalTraining) => void
  onTrainingCut?: (training: PersonalTraining) => void
  cutTrainingId?: string | null
  copiedTrainingId?: string | null
  pasteActive?: boolean
  // null = drop into the all-day lane (no hour); a string = drop onto that hour on the grid
  onPasteAt?: (date: string, time: string | null) => void
  isCoachView?: boolean
}

export function TrainingWeekCalendar({
  startDate, trainings, reservations, invitations, invitationLabel,
  onPrevWeek, onNextWeek, onToday,
  onTrainingClick, onReservationClick, onInvitationClick, onDayClick,
  onTrainingMove, onTrainingCopy, onTrainingCut,
  cutTrainingId, copiedTrainingId, pasteActive, onPasteAt, isCoachView,
}: TrainingWeekCalendarProps) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()
  const scrollRef = useRef<HTMLDivElement>(null)
  const dayColumnRefs = useRef<(HTMLDivElement | null)[]>([])

  const days = useMemo(() => {
    const start = new Date(startDate)
    return Array.from({ length: 7 }, (_, i) => format(addDays(start, i), 'yyyy-MM-dd'))
  }, [startDate])

  const dayObjs = useMemo(() => days.map((date) => ({ date })), [days])

  const { dragState, isBeingDragged, wasJustDragged, didJustDrag, onSlotPointerDown, onResizePointerDown, longPressSlotId } =
    useSlotDrag({
      days: dayObjs,
      dayColumnRefs,
      snapMinutes: 30,
      enabled: !!onTrainingMove,
      onDrop: (trainingId, newDate, newStart, newEnd, oldDate, oldStart, oldEnd) => {
        if (newDate === oldDate && newStart === oldStart && newEnd === oldEnd) return
        onTrainingMove?.(trainingId, newDate, newStart, newEnd)
      },
    })

  const hours = useMemo(() => Array.from({ length: TOTAL_HOURS }, (_, i) => START_HOUR + i), [])

  const weekdays = useMemo(
    () => days.map((d) => format(new Date(d), 'EEEEEE', { locale })),
    [days, locale],
  )

  // Only timed entries land in the hour grid; untimed trainings and all-day invitations
  // (event invites with no times) go to the all-day lane above it.
  const byDay = useMemo(() => {
    const map = new Map<string, DayEntries>()
    for (const date of days) {
      map.set(date, splitDay(date, trainings, reservations, invitations))
    }
    return map
  }, [days, trainings, reservations, invitations])

  // Auto-scroll to today's column on mobile (same behavior as the public week view)
  useEffect(() => {
    if (scrollRef.current) {
      const todayIndex = days.findIndex((d) => isToday(new Date(d)))
      if (todayIndex > 0) {
        scrollRef.current.scrollLeft = todayIndex * 130 - 20
      }
    }
  }, [days])

  const start = new Date(days[0])
  const end = new Date(days[6])

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-surface-800">
        <button
          aria-label={t('nav.prevWeek')}
          onClick={onPrevWeek}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold text-surface-100">
            {format(start, 'd MMM', { locale })} - {format(end, 'd MMM yyyy', { locale })}
          </h2>
          <button
            onClick={onToday}
            className="px-3 py-1 text-xs font-medium text-primary-400 border border-primary-500/30 rounded-lg hover:bg-primary-500/10 transition-colors"
          >
            {t('nav.today')}
          </button>
        </div>

        <button
          aria-label={t('nav.nextWeek')}
          onClick={onNextWeek}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Grid */}
      <div ref={scrollRef} className="overflow-x-auto">
        <div className="min-w-[900px]">
          {/* Column headers */}
          <div className="grid border-b border-surface-800" style={{ gridTemplateColumns: '60px repeat(7, 1fr)' }}>
            <div className="py-2" />
            {days.map((date, i) => {
              const d = new Date(date)
              const today = isToday(d)
              return (
                <div
                  key={date}
                  className={clsx('py-2 text-center border-l border-surface-800', today && 'bg-primary-500/10')}
                >
                  <div className="text-xs text-surface-500 font-medium capitalize">{weekdays[i]}</div>
                  <div className={clsx('text-sm font-semibold', today ? 'text-primary-400' : 'text-surface-200')}>
                    {format(d, 'd')}
                  </div>
                </div>
              )
            })}
          </div>

          {/* All-day lane: untimed trainings + all-day invitations, pinned above the hour grid.
              Untimed is common here, so the lane is sized for two chips without growing, and the
              gutter uses the SHORT label with bottom clearance — the first hour label ("7:00")
              is shifted 8px up into this row, and the long form wording collided with it. */}
          <div className="grid border-b border-surface-800" style={{ gridTemplateColumns: '60px repeat(7, 1fr)' }}>
            <div className="flex items-start justify-end pr-2 pt-1.5 pb-3 text-[10px] leading-tight text-surface-500">
              {t('detail.allDay')}
            </div>
            {days.map((date) => {
              const today = isToday(new Date(date))
              const allDay = byDay.get(date)
              return (
                <div
                  key={date}
                  className={clsx(
                    'relative min-h-14 p-1 pb-3 space-y-0.5 border-l border-surface-800 transition-colors',
                    today && 'bg-primary-500/5',
                    pasteActive ? 'cursor-copy hover:bg-primary-500/10' : 'cursor-pointer hover:bg-surface-800/40',
                  )}
                  onClick={(e) => {
                    if ((e.target as HTMLElement).closest('button')) return
                    // An armed clipboard makes this a drop target, not an add target — without
                    // it, the one place an untimed entry (and every task) can land was the one
                    // place that opened the create form instead of pasting.
                    // Explicit null: dropping here means "no hour", not "keep the source's".
                    if (pasteActive && onPasteAt) {
                      onPasteAt(date, null)
                      return
                    }
                    // All-day cell → add an untimed training (no time passed)
                    onDayClick(date)
                  }}
                >
                  {allDay?.allDayTrainings.map((tr) => (
                    <TrainingBlock
                      key={tr.id}
                      training={tr}
                      onClick={() => onTrainingClick(tr)}
                      density="chip"
                    />
                  ))}
                  {allDay?.allDayInvitations.map((inv, i) => (
                    <InvitationBlock
                      key={`inv-${i}-${inv.slotId ?? inv.eventId}`}
                      invitation={inv}
                      label={invitationLabel}
                      onClick={() => onInvitationClick(inv)}
                      density="chip"
                    />
                  ))}
                </div>
              )
            })}
          </div>

          {/* Time grid */}
          <div className="relative grid" style={{ gridTemplateColumns: '60px repeat(7, 1fr)' }}>
            {/* Hour labels */}
            <div className="sticky left-0 z-10 bg-surface-900">
              {hours.map((hour) => (
                <div
                  key={hour}
                  className="relative border-b border-surface-600/50 text-right pr-2 text-xs text-surface-500"
                  style={{ height: HOUR_HEIGHT }}
                >
                  <span className="relative -top-2">{`${hour}:00`}</span>
                </div>
              ))}
            </div>

            {/* Day columns */}
            {days.map((date, dayIndex) => {
              const today = isToday(new Date(date))
              const items = byDay.get(date)?.timed ?? []
              return (
                <div
                  key={date}
                  ref={(el) => { dayColumnRefs.current[dayIndex] = el }}
                  className={clsx(
                    'relative border-l border-surface-800',
                    pasteActive ? 'cursor-crosshair' : 'cursor-pointer',
                    today && 'bg-primary-500/5',
                  )}
                  style={{ height: TOTAL_HOURS * HOUR_HEIGHT }}
                  onClick={(e) => {
                    // A drop/resize ends with a click on the column (mousedown/mouseup
                    // common ancestor) — without this guard every drag would also open
                    // the add-training form
                    if (didJustDrag()) return
                    // Blocks and their hover actions (incl. the non-button resize handle)
                    // must not read as an empty-space click
                    const target = e.target as HTMLElement
                    if (target.closest('button') || target.closest('[data-admin-action]')) return
                    const rect = e.currentTarget.getBoundingClientRect()
                    const time = clickToTime(e.clientY - rect.top)
                    if (pasteActive && onPasteAt) {
                      onPasteAt(date, time)
                      return
                    }
                    onDayClick(date, time)
                  }}
                >
                  {/* Hour grid lines */}
                  {hours.map((hour) => (
                    <div
                      key={hour}
                      className="absolute w-full border-b border-surface-600/50"
                      style={{ top: (hour - START_HOUR) * HOUR_HEIGHT, height: HOUR_HEIGHT }}
                    >
                      <div className="absolute w-full border-b border-surface-700/40" style={{ top: '50%' }} />
                    </div>
                  ))}

                  {/* Blocks */}
                  {items.map((item) => {
                    const top = ((item.startMin - START_HOUR * 60) / 60) * HOUR_HEIGHT
                    // Min 30px for tappability, but never past the grid bottom (late entries)
                    const height = Math.min(
                      Math.max(((item.endMin - item.startMin) / 60) * HOUR_HEIGHT, 30),
                      TOTAL_HOURS * HOUR_HEIGHT - top,
                    )
                    const width = 100 / item.lanes
                    const style: React.CSSProperties = {
                      top,
                      height,
                      left: `calc(${item.lane * width}% + 2px)`,
                      width: `calc(${width}% - 4px)`,
                      zIndex: 10,
                    }
                    if (item.training) {
                      const tr = item.training
                      // COMPLETED entries are history: copy allowed (re-plan it forward),
                      // but no move/cut/resize — the record must stay where it happened
                      const movable = !!onTrainingMove && tr.status !== 'COMPLETED'
                      return (
                        <TrainingBlock
                          key={item.key}
                          training={tr}
                          onClick={() => {
                            if (wasJustDragged(tr.id)) return
                            onTrainingClick(tr)
                          }}
                          style={style}
                          clampedTop={item.clampedTop}
                          clampedBottom={item.clampedBottom}
                          onCopy={onTrainingCopy ? () => onTrainingCopy(tr) : undefined}
                          onCut={onTrainingCut && tr.status !== 'COMPLETED' ? () => onTrainingCut(tr) : undefined}
                          isCut={cutTrainingId === tr.id}
                          isCopied={copiedTrainingId === tr.id}
                          onPointerDown={movable
                            ? (e) => onSlotPointerDown(tr.id, date, tr.startTime!.slice(0, 5), tr.endTime!.slice(0, 5), e)
                            : undefined}
                          onResizePointerDown={movable
                            ? (e) => onResizePointerDown(tr.id, date, tr.startTime!.slice(0, 5), tr.endTime!.slice(0, 5), e)
                            : undefined}
                          isDragging={isBeingDragged(tr.id)}
                          isLongPressing={longPressSlotId === tr.id}
                        />
                      )
                    }
                    return item.invitation ? (
                      <InvitationBlock
                        key={item.key}
                        invitation={item.invitation}
                        label={invitationLabel}
                        onClick={() => onInvitationClick(item.invitation!)}
                        style={style}
                      />
                    ) : (
                      <ReservationBlock
                        key={item.key}
                        reservation={item.reservation!}
                        label={t('overlay.reservation')}
                        onClick={() => onReservationClick(item.reservation!)}
                        style={style}
                        isCoachView={isCoachView}
                      />
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {/* Drag ghost — indigo to match training blocks (admin calendar uses primary) */}
      {dragState?.ghost && (
        <div
          className="fixed z-50 pointer-events-none rounded border-2 border-indigo-400 bg-indigo-500/25"
          style={{
            left: dragState.ghost.left,
            top: dragState.ghost.top,
            width: dragState.ghost.width,
            height: dragState.ghost.height,
          }}
        />
      )}
    </div>
  )
}
