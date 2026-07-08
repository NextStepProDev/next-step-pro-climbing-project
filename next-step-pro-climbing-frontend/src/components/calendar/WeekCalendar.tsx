import { useMemo, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Scissors, Copy, Bell, Check, X } from 'lucide-react'
import { format, isToday, isBefore, startOfDay } from 'date-fns'
import clsx from 'clsx'
import type { WeekDay, TimeSlot, EventSummary } from '../../types'
import type { EventColorMap } from '../../utils/events'
import { getEventColorByIndex } from '../../utils/events'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { useSlotDrag } from '../../hooks/useSlotDrag'
import { useAuth } from '../../context/AuthContext'

const HOUR_HEIGHT = 40
const START_HOUR = 7
const END_HOUR = 23
const TOTAL_HOURS = END_HOUR - START_HOUR

interface WeekCalendarProps {
  startDate: string
  days: WeekDay[]
  events: EventSummary[]
  eventColorMap: EventColorMap
  onPrevWeek: () => void
  onNextWeek: () => void
  onToday: () => void
  onSlotClick: (slotId: string) => void
  onEventClick: (event: EventSummary) => void
  onDayClick: (date: string) => void
  // Admin drag-and-drop
  isAdmin?: boolean
  onSlotDrop?: (slotId: string, newDate: string, newStartTime: string, newEndTime: string, oldDate: string, oldStartTime: string, oldEndTime: string) => void
  onSlotCut?: (slot: TimeSlot, date: string) => void
  onSlotCopy?: (slot: TimeSlot, date: string) => void
  cutSlotId?: string
  copiedSlotId?: string
  onColumnClick?: (date: string, time: string) => void
  onNotifyParticipants?: (slotId: string) => void
  pendingSlotId?: string
  onConfirmSlotMove?: (slotId: string) => void
  onCancelSlotMove?: (slotId: string) => void
}

function getSlotPosition(startTime: string, endTime: string) {
  const [startH, startM] = startTime.split(':').map(Number)
  const [endH, endM] = endTime.split(':').map(Number)
  return {
    top: ((startH - START_HOUR) + startM / 60) * HOUR_HEIGHT,
    height: Math.max(((endH - startH) + (endM - startM) / 60) * HOUR_HEIGHT, 30),
  }
}

const GRID_START_MIN = START_HOUR * 60
const GRID_END_MIN = END_HOUR * 60

function timeToMin(time: string): number {
  const [h, m] = time.split(':').map(Number)
  return h * 60 + m
}

/** Returns the segments of [start, end] (in minutes, clamped to the grid) NOT covered by any
 *  `covers` interval — used to draw the "unavailable" fill broken around slots/availability windows. */
function subtractIntervals(start: number, end: number, covers: Array<[number, number]>): Array<[number, number]> {
  const from = Math.max(start, GRID_START_MIN)
  const to = Math.min(end, GRID_END_MIN)
  if (to <= from) return []
  const sorted = covers
    .map(([s, e]) => [Math.max(s, from), Math.min(e, to)] as [number, number])
    .filter(([s, e]) => e > s)
    .sort((a, b) => a[0] - b[0])
  const segments: Array<[number, number]> = []
  let cursor = from
  for (const [s, e] of sorted) {
    if (s > cursor) segments.push([cursor, s])
    cursor = Math.max(cursor, e)
  }
  if (cursor < to) segments.push([cursor, to])
  return segments
}

function getSlotColors(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return 'bg-green-500/25 border-green-500/60 text-green-300 hover:bg-green-500/40'
    case 'FULL':
      return 'bg-amber-600/15 border-amber-500/35 text-amber-300/90'
    case 'BLOCKED':
      return 'bg-surface-700/50 border-surface-600/50 text-surface-400'
    case 'BOOKING_CLOSED':
      return 'bg-surface-700/40 border-surface-600/40 text-surface-400'
    case 'PAST':
      return 'bg-surface-800/30 border-surface-700/30 text-surface-500'
    case 'AVAILABILITY_WINDOW':
      return 'bg-teal-600/30 border-teal-500/50 text-teal-300 hover:bg-teal-600/40'
    default:
      return 'bg-surface-700/50 border-surface-600/50 text-surface-400'
  }
}

function getStatusLabel(status: string, t: (key: string) => string): string {
  switch (status) {
    case 'AVAILABLE':
      return t('day.available')
    case 'FULL':
      return t('day.full')
    case 'BLOCKED':
      return t('day.blocked')
    case 'BOOKING_CLOSED':
      return t('day.bookingClosed')
    case 'PAST':
      return t('day.past')
    case 'AVAILABILITY_WINDOW':
      return t('day.callToBook')
    default:
      return ''
  }
}

function snapMinutesToTime(relativeMinutes: number): string {
  const absMinutes = START_HOUR * 60 + Math.max(0, Math.min(relativeMinutes, TOTAL_HOURS * 60))
  const h = Math.floor(absMinutes / 60)
  const m = absMinutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

export function WeekCalendar({
  startDate,
  days,
  events,
  eventColorMap,
  onPrevWeek,
  onNextWeek,
  onToday,
  onSlotClick,
  onEventClick,
  onDayClick,
  isAdmin = false,
  onSlotDrop,
  onSlotCut,
  onSlotCopy,
  cutSlotId,
  copiedSlotId,
  onColumnClick,
  onNotifyParticipants,
  pendingSlotId,
  onConfirmSlotMove,
  onCancelSlotMove,
}: WeekCalendarProps) {
  const { t } = useTranslation('calendar')
  const { isAuthenticated } = useAuth()
  const locale = useDateLocale()
  const scrollRef = useRef<HTMLDivElement>(null)
  const dayColumnRefs = useRef<(HTMLDivElement | null)[]>([])

  const { dragState, isBeingDragged, wasJustDragged, onSlotPointerDown, onResizePointerDown, longPressSlotId } = useSlotDrag({
    days,
    dayColumnRefs,
    onDrop: onSlotDrop ?? (() => {}),
    enabled: isAdmin && !!onSlotDrop,
  })

  const start = useMemo(() => new Date(startDate), [startDate])
  const end = useMemo(() => {
    const d = new Date(startDate)
    d.setDate(d.getDate() + 6)
    return d
  }, [startDate])

  const hours = useMemo(
    () => Array.from({ length: TOTAL_HOURS }, (_, i) => START_HOUR + i),
    [],
  )

  const weekdays = [
    t('weekdays.mon'), t('weekdays.tue'), t('weekdays.wed'),
    t('weekdays.thu'), t('weekdays.fri'), t('weekdays.sat'), t('weekdays.sun'),
  ]

  const dayEventsMap = useMemo(() => {
    const map = new Map<string, EventSummary[]>()
    events.forEach((event) => {
      const eStart = new Date(event.startDate)
      const eEnd = new Date(event.endDate)
      for (let d = new Date(eStart); d <= eEnd; d.setDate(d.getDate() + 1)) {
        const key = format(d, 'yyyy-MM-dd')
        const list = map.get(key) || []
        list.push(event)
        map.set(key, list)
      }
    })
    return map
  }, [events])

  // Auto-scroll to today's column on mobile
  useEffect(() => {
    if (scrollRef.current) {
      const todayIndex = days.findIndex(d => isToday(new Date(d.date)))
      if (todayIndex > 0) {
        const columnWidth = 130
        scrollRef.current.scrollLeft = todayIndex * columnWidth - 20
      }
    }
  }, [days])

  const inCutMode = isAdmin && !!cutSlotId
  const inCopyMode = isAdmin && !!copiedSlotId
  const inPasteMode = inCutMode || inCopyMode

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-surface-800">
        <button
          aria-label={t('week.previousWeek')}
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
            {t('week.today')}
          </button>
        </div>

        <button
          aria-label={t('week.nextWeek')}
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
            {days.map((day, i) => {
              const date = new Date(day.date)
              const today = isToday(date)
              const past = isBefore(date, startOfDay(new Date()))
              const dayEvents = dayEventsMap.get(day.date) || []
              return (
                <button
                  key={day.date}
                  onClick={() => onDayClick(day.date)}
                  className={clsx(
                    'py-2 text-center border-l border-surface-800 transition-colors',
                    today && 'bg-primary-500/10',
                    past && 'opacity-60',
                    !past && 'hover:bg-surface-800/50 cursor-pointer',
                  )}
                >
                  <div className="text-xs text-surface-500 font-medium">{weekdays[i]}</div>
                  <div className={clsx(
                    'text-sm font-semibold',
                    today ? 'text-primary-400' : 'text-surface-200',
                  )}>
                    {format(date, 'd')}
                  </div>
                  {dayEvents.length > 0 && !past && (
                    <div className={clsx(
                      "w-1.5 h-1.5 rounded-full mx-auto mt-0.5",
                      dayEvents.every(e => e.eventType === 'UNAVAILABLE') ? 'bg-slate-500'
                        : dayEvents.every(e => e.eventType === 'CONTACT_DAY') ? 'bg-indigo-500'
                        : dayEvents.every(e => e.currentParticipants >= e.maxParticipants) ? 'bg-amber-500'
                        : 'bg-primary-500'
                    )} />
                  )}
                </button>
              )
            })}
          </div>

          {/* Time grid */}
          <div className="relative grid" style={{ gridTemplateColumns: '60px repeat(7, 1fr)' }}>
            {/* Hour labels (sticky left) */}
            <div className="sticky left-0 z-10 bg-surface-900">
              {hours.map((hour) => (
                <div
                  key={hour}
                  className="relative border-b border-surface-600/50 text-right pr-2 text-xs text-surface-500"
                  style={{ height: HOUR_HEIGHT }}
                >
                  <span className="relative -top-2">{`${hour}:00`}</span>
                  {/* Half-hour tick */}
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[9px] text-surface-600">·</span>
                </div>
              ))}
            </div>

            {/* Day columns */}
            {days.map((day, dayIndex) => {
              const date = new Date(day.date)
              const today = isToday(date)
              const past = isBefore(date, startOfDay(new Date()))
              const dayEvents = dayEventsMap.get(day.date) || []

              return (
                <div
                  key={day.date}
                  ref={(el) => { dayColumnRefs.current[dayIndex] = el }}
                  className={clsx(
                    'relative border-l border-surface-800',
                    today && 'bg-primary-500/5',
                    past && 'opacity-50',
                    inPasteMode && !past && 'cursor-crosshair',
                  )}
                  style={{ height: TOTAL_HOURS * HOUR_HEIGHT }}
                  onClick={(e) => {
                    if (!inPasteMode || !onColumnClick) return
                    if ((e.target as HTMLElement).closest('button')) return
                    const rect = e.currentTarget.getBoundingClientRect()
                    const relY = e.clientY - rect.top
                    const rawMin = Math.round(relY / HOUR_HEIGHT * 60 / 15) * 15
                    onColumnClick(day.date, snapMinutesToTime(rawMin))
                  }}
                >
                  {/* Hour grid lines */}
                  {hours.map((hour) => (
                    <div
                      key={hour}
                      className="absolute w-full border-b border-surface-600/50"
                      style={{ top: (hour - START_HOUR) * HOUR_HEIGHT, height: HOUR_HEIGHT }}
                    >
                      {/* Half-hour line */}
                      <div className="absolute w-full border-b border-surface-700/40" style={{ top: '50%' }} />
                    </div>
                  ))}

                  {/* Unavailable (absence) fill — spans the day, broken around real slots/windows */}
                  {dayEvents.filter((event) => event.eventType === 'UNAVAILABLE').flatMap((event) => {
                    const isFirst = day.date === event.startDate
                    const isLast = day.date === event.endDate
                    const rangeStart = event.startTime && isFirst ? timeToMin(event.startTime) : GRID_START_MIN
                    const rangeEnd = event.endTime && isLast ? timeToMin(event.endTime) : GRID_END_MIN
                    const slotCovers = day.slots.map((s) => [timeToMin(s.startTime), timeToMin(s.endTime)] as [number, number])
                    const segments = subtractIntervals(rangeStart, rangeEnd, slotCovers)
                    // Label goes on the tallest segment so it stays visible even when a slot
                    // covers the spot where it would normally sit (the natural top of the range).
                    let labelIdx = 0
                    for (let k = 1; k < segments.length; k++) {
                      if (segments[k][1] - segments[k][0] > segments[labelIdx][1] - segments[labelIdx][0]) labelIdx = k
                    }
                    return segments.map(([segStart, segEnd], i) => {
                      const top = (segStart - GRID_START_MIN) / 60 * HOUR_HEIGHT
                      const height = (segEnd - segStart) / 60 * HOUR_HEIGHT
                      return (
                        <button
                          key={`${event.id}-unavail-${i}`}
                          onClick={() => onEventClick(event)}
                          title={event.title}
                          className="group/unavail absolute left-1 right-1 z-[1] rounded border border-slate-500/40 bg-slate-600/25 hover:bg-slate-600/35 text-slate-300 transition-colors cursor-pointer overflow-hidden text-left"
                          style={{ top, height }}
                        >
                          {i === labelIdx && height >= 24 && (
                            <div className="px-1.5 py-0.5">
                              <div className="text-[11px] font-semibold leading-tight truncate">{t('event.unavailable')}</div>
                              <div className="text-[10px] leading-tight truncate opacity-80">{event.title}</div>
                            </div>
                          )}
                        </button>
                      )
                    })
                  })}

                  {/* Range tint — enrollable events fill their time span behind the slots.
                      First day from startTime → grid end, middle days full grid, last day
                      grid start → endTime; all-day (no time) fills the whole grid. Sits under
                      slots (z-[1] < z-10) so slots stay visible + clickable; clicking an empty
                      part of the tint opens the event modal (same as the top bar). */}
                  {dayEvents
                    .filter((event) => event.eventType !== 'UNAVAILABLE' && event.eventType !== 'CONTACT_DAY')
                    .map((event) => {
                      const isFirst = day.date === event.startDate
                      const isLast = day.date === event.endDate
                      const rangeStart = event.startTime && isFirst ? timeToMin(event.startTime) : GRID_START_MIN
                      const rangeEnd = event.endTime && isLast ? timeToMin(event.endTime) : GRID_END_MIN
                      const from = Math.max(rangeStart, GRID_START_MIN)
                      const to = Math.min(rangeEnd, GRID_END_MIN)
                      if (to <= from) return null
                      const top = (from - GRID_START_MIN) / 60 * HOUR_HEIGHT
                      const height = (to - from) / 60 * HOUR_HEIGHT
                      const color = eventColorMap.get(event.id) ?? getEventColorByIndex(event.id, event.eventType, event.currentParticipants >= event.maxParticipants)
                      return (
                        <button
                          key={`${event.id}-tint`}
                          onClick={() => onEventClick(event)}
                          title={event.title}
                          aria-label={event.title}
                          className={clsx(
                            'absolute left-0.5 right-0.5 z-[1] overflow-hidden rounded-sm border-l-2 cursor-pointer',
                            color.barBorder,
                          )}
                          style={{ top, height }}
                        >
                          <span className={clsx('absolute inset-0 opacity-10', color.dot)} />
                        </button>
                      )
                    })}

                  {/* Event title bars — anchored at the event's start hour on its first day
                      (grid top on continuation days / all-day, matching where the tint begins).
                      Bars sharing the same anchor stack downward so they don't overlap. */}
                  {(() => {
                    const stackByTop = new Map<number, number>()
                    return dayEvents.filter((event) => event.eventType !== 'UNAVAILABLE').map((event) => {
                      const isFirst = day.date === event.startDate
                      const anchorMin = event.startTime && isFirst
                        ? Math.max(timeToMin(event.startTime), GRID_START_MIN)
                        : GRID_START_MIN
                      const baseTop = (anchorMin - GRID_START_MIN) / 60 * HOUR_HEIGHT
                      const stackIdx = stackByTop.get(baseTop) ?? 0
                      stackByTop.set(baseTop, stackIdx + 1)
                      const color = eventColorMap.get(event.id) ?? getEventColorByIndex(event.id, event.eventType, event.currentParticipants >= event.maxParticipants)
                      return (
                        <button
                          key={event.id}
                          onClick={() => onEventClick(event)}
                          title={event.title}
                          className={clsx(
                            "absolute left-0.5 right-0.5 z-20 px-1 py-0.5 text-[11px] leading-snug font-medium rounded border truncate transition-colors cursor-pointer",
                            color.barBg, color.barBorder, color.barText, color.barHover
                          )}
                          style={{ top: baseTop + stackIdx * 22 }}
                        >
                          {event.title}
                        </button>
                      )
                    })
                  })()}

                  {/* Slots */}
                  {day.slots.map((slot: TimeSlot) => {
                    const { top, height } = getSlotPosition(slot.startTime, slot.endTime)
                    const isClickable = isAdmin || slot.status === 'AVAILABLE' || slot.status === 'FULL' || slot.status === 'AVAILABILITY_WINDOW' || slot.status === 'BOOKING_CLOSED' || slot.isUserRegistered
                    const showTitle = height >= 30
                    const dragging = isBeingDragged(slot.id)
                    const isCut = cutSlotId === slot.id
                    const isCopied = copiedSlotId === slot.id
                    // Status PAST przychodzi od godziny STARTU — dla admina slot jest „zakończony"
                    // dopiero po godzinie końca, żeby trwający slot dało się jeszcze przesunąć/edytować.
                    const hasEnded = slot.status === 'PAST' && new Date(`${day.date}T${slot.endTime}`) < new Date()
                    const isDraggable = isAdmin && !hasEnded
                    const isPending = pendingSlotId === slot.id
                    const isLongPressing = longPressSlotId === slot.id
                    // „Na zaproszenie" (fioletowy) tylko dla niezalogowanych — zalogowany nie-zaproszony
                    // widzi zwykłe „pełne". Zaproszony i tak ma status AVAILABLE.
                    const invitedOnly = !isAuthenticated && slot.status === 'FULL' && slot.reservedSeats > 0 && !slot.isReservedForUser && !slot.isUserRegistered

                    return (
                      <div
                        key={slot.id}
                        className={clsx(
                          'group absolute left-1 right-1 rounded border transition-colors z-10',
                          isPending ? 'overflow-visible' : 'overflow-hidden',
                          isPending
                            ? 'bg-red-600/40 border-red-400/70 text-red-200'
                            : invitedOnly
                            ? 'bg-violet-600/30 border-violet-500/50 text-violet-200'
                            : getSlotColors(slot.status),
                          isDraggable && !dragging && 'cursor-grab',
                          dragging && 'opacity-30 cursor-grabbing',
                          isCut && 'ring-2 ring-dashed ring-amber-400 opacity-60',
                          isCopied && 'ring-2 ring-dashed ring-primary-400',
                          isLongPressing && !dragging && 'ring-2 ring-primary-400/60 z-30',
                          slot.isUserRegistered && !isPending && 'ring-1 ring-primary-400',
                        )}
                        style={{ top, height }}
                        onPointerDown={isDraggable
                          ? (e) => onSlotPointerDown(slot.id, day.date, slot.startTime, slot.endTime, e)
                          : undefined
                        }
                      >
                        {/* Slot content — clickable area */}
                        <button
                          onClick={() => {
                            if (wasJustDragged(slot.id)) return
                            if (isClickable) onSlotClick(slot.id)
                          }}
                          disabled={!isClickable}
                          className={clsx(
                            'w-full h-full px-1.5 py-0.5 text-left',
                            isClickable && 'cursor-pointer',
                            !isClickable && 'cursor-default',
                            isDraggable && 'select-none',
                          )}
                        >
                          <div className="text-[11px] font-semibold leading-tight truncate">
                            {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                          </div>
                          {showTitle && (
                            <div className="text-[10px] leading-tight truncate opacity-80">
                              {slot.status === 'FULL' && !slot.isUserRegistered
                                ? (invitedOnly ? t('day.invitedOnly') : t('day.fullWaitlist'))
                                : slot.eventTitle || getStatusLabel(slot.status, t)}
                            </div>
                          )}
                          {slot.isUserRegistered && (
                            <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 bg-primary-400 rounded-full" />
                          )}
                        </button>

                        {/* Admin action buttons */}
                        {isAdmin && !hasEnded && (
                          <div
                            data-admin-action
                            className={clsx(
                              'absolute flex z-20',
                              isPending
                                ? 'top-0 right-0 gap-1 p-0.5'
                                : 'top-0.5 right-0.5 gap-0.5 opacity-0 group-hover:opacity-100'
                            )}
                          >
                            {isPending ? (
                              <>
                                <button
                                  data-admin-action
                                  onClick={(e) => { e.stopPropagation(); onConfirmSlotMove?.(slot.id) }}
                                  className="p-1.5 sm:p-0.5 rounded bg-green-900/80 text-green-300 hover:text-green-100 transition-colors"
                                  title="Zatwierdź"
                                >
                                  <Check className="w-4 h-4 sm:w-3 sm:h-3" />
                                </button>
                                <button
                                  data-admin-action
                                  onClick={(e) => { e.stopPropagation(); onCancelSlotMove?.(slot.id) }}
                                  className="p-1.5 sm:p-0.5 rounded bg-red-900/80 text-red-300 hover:text-red-100 transition-colors"
                                  title="Anuluj"
                                >
                                  <X className="w-4 h-4 sm:w-3 sm:h-3" />
                                </button>
                              </>
                            ) : (
                              <>
                                {!slot.isAvailabilityWindow && slot.currentParticipants > 0 && onNotifyParticipants && (
                                  <button
                                    data-admin-action
                                    onClick={(e) => { e.stopPropagation(); onNotifyParticipants(slot.id) }}
                                    className="p-0.5 rounded bg-surface-900/70 text-surface-300 hover:text-amber-300 transition-colors"
                                    title="Powiadom uczestników"
                                  >
                                    <Bell className="w-2.5 h-2.5" />
                                  </button>
                                )}
                                {onSlotCopy && (
                                  <button
                                    data-admin-action
                                    onClick={(e) => { e.stopPropagation(); onSlotCopy(slot, day.date) }}
                                    className="p-0.5 rounded bg-surface-900/70 text-surface-300 hover:text-primary-300 transition-colors"
                                    title="Kopiuj slot"
                                  >
                                    <Copy className="w-2.5 h-2.5" />
                                  </button>
                                )}
                                {onSlotCut && (
                                  <button
                                    data-admin-action
                                    onClick={(e) => { e.stopPropagation(); onSlotCut(slot, day.date) }}
                                    className="p-0.5 rounded bg-surface-900/70 text-surface-300 hover:text-amber-300 transition-colors"
                                    title="Wytnij slot"
                                  >
                                    <Scissors className="w-2.5 h-2.5" />
                                  </button>
                                )}
                              </>
                            )}
                          </div>
                        )}

                        {/* Resize handle */}
                        {isDraggable && (
                          <div
                            data-admin-action
                            className="absolute bottom-0 left-0 right-0 h-2 cursor-ns-resize z-20"
                            style={{ touchAction: 'none' }}
                            onPointerDown={(e) => onResizePointerDown(slot.id, day.date, slot.startTime, slot.endTime, e)}
                          />
                        )}
                      </div>
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {/* Drag ghost */}
      {dragState?.ghost && (
        <div
          className="fixed z-50 pointer-events-none rounded border-2 border-primary-400 bg-primary-500/25"
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
