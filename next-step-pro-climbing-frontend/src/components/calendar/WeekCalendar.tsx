import { useMemo, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Scissors, Bell, Check, X } from 'lucide-react'
import { format, isToday, isBefore, startOfDay } from 'date-fns'
import clsx from 'clsx'
import type { WeekDay, TimeSlot, EventSummary } from '../../types'
import { getEventColorByIndex } from '../../utils/events'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { useSlotDrag } from '../../hooks/useSlotDrag'

const HOUR_HEIGHT = 40
const START_HOUR = 7
const END_HOUR = 23
const TOTAL_HOURS = END_HOUR - START_HOUR

interface WeekCalendarProps {
  startDate: string
  days: WeekDay[]
  events: EventSummary[]
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
  cutSlotId?: string
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

function getSlotColors(status: string): string {
  switch (status) {
    case 'AVAILABLE':
      return 'bg-primary-600/30 border-primary-500/50 text-primary-300 hover:bg-primary-600/40'
    case 'FULL':
      return 'bg-amber-600/30 border-amber-500/50 text-amber-300'
    case 'BLOCKED':
      return 'bg-dark-700/50 border-dark-600/50 text-dark-400'
    case 'BOOKING_CLOSED':
      return 'bg-dark-700/40 border-dark-600/40 text-dark-400'
    case 'PAST':
      return 'bg-dark-800/30 border-dark-700/30 text-dark-500'
    case 'AVAILABILITY_WINDOW':
      return 'bg-violet-600/30 border-violet-500/50 text-violet-300 hover:bg-violet-600/40'
    default:
      return 'bg-dark-700/50 border-dark-600/50 text-dark-400'
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
  onPrevWeek,
  onNextWeek,
  onToday,
  onSlotClick,
  onEventClick,
  onDayClick,
  isAdmin = false,
  onSlotDrop,
  onSlotCut,
  cutSlotId,
  onColumnClick,
  onNotifyParticipants,
  pendingSlotId,
  onConfirmSlotMove,
  onCancelSlotMove,
}: WeekCalendarProps) {
  const { t } = useTranslation('calendar')
  const locale = useDateLocale()
  const scrollRef = useRef<HTMLDivElement>(null)
  const dayColumnRefs = useRef<(HTMLDivElement | null)[]>([])

  const { dragState, isBeingDragged, wasJustDragged, onSlotPointerDown, onResizePointerDown } = useSlotDrag({
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

  return (
    <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-dark-800">
        <button
          onClick={onPrevWeek}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3">
          <h2 className="text-lg font-semibold text-dark-100">
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
          onClick={onNextWeek}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Grid */}
      <div ref={scrollRef} className="overflow-x-auto">
        <div className="min-w-[900px]">
          {/* Column headers */}
          <div className="grid border-b border-dark-800" style={{ gridTemplateColumns: '60px repeat(7, 1fr)' }}>
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
                    'py-2 text-center border-l border-dark-800 transition-colors',
                    today && 'bg-primary-500/10',
                    past && 'opacity-50',
                    !past && 'hover:bg-dark-800/50 cursor-pointer',
                  )}
                >
                  <div className="text-xs text-dark-500 font-medium">{weekdays[i]}</div>
                  <div className={clsx(
                    'text-sm font-semibold',
                    today ? 'text-primary-400' : 'text-dark-200',
                  )}>
                    {format(date, 'd')}
                  </div>
                  {dayEvents.length > 0 && !past && (
                    <div className={clsx(
                      "w-1.5 h-1.5 rounded-full mx-auto mt-0.5",
                      dayEvents.every(e => e.eventType === 'CONTACT_DAY') ? 'bg-violet-500'
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
            <div className="sticky left-0 z-10 bg-dark-900">
              {hours.map((hour) => (
                <div
                  key={hour}
                  className="relative border-b border-dark-600/50 text-right pr-2 text-xs text-dark-500"
                  style={{ height: HOUR_HEIGHT }}
                >
                  <span className="relative -top-2">{`${hour}:00`}</span>
                  {/* Half-hour tick */}
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[9px] text-dark-600">·</span>
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
                    'relative border-l border-dark-800',
                    today && 'bg-primary-500/5',
                    past && 'opacity-40',
                    inCutMode && !past && 'cursor-crosshair',
                  )}
                  style={{ height: TOTAL_HOURS * HOUR_HEIGHT }}
                  onClick={(e) => {
                    if (!inCutMode || !onColumnClick) return
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
                      className="absolute w-full border-b border-dark-600/50"
                      style={{ top: (hour - START_HOUR) * HOUR_HEIGHT, height: HOUR_HEIGHT }}
                    >
                      {/* Half-hour line */}
                      <div className="absolute w-full border-b border-dark-700/40" style={{ top: '50%' }} />
                    </div>
                  ))}

                  {/* Event bars at top area */}
                  {dayEvents.map((event, eventIndex) => {
                    const color = getEventColorByIndex(event.id, event.eventType, event.currentParticipants >= event.maxParticipants)
                    return (
                      <button
                        key={event.id}
                        onClick={() => onEventClick(event)}
                        className={clsx(
                          "absolute left-0.5 right-0.5 z-20 px-1 py-0.5 text-[10px] leading-tight font-medium rounded border truncate transition-colors cursor-pointer",
                          color.barBg, color.barBorder, color.barText, color.barHover
                        )}
                        style={{ top: eventIndex * 20 }}
                      >
                        {event.title}
                      </button>
                    )
                  })}

                  {/* Slots */}
                  {day.slots.map((slot: TimeSlot) => {
                    const { top, height } = getSlotPosition(slot.startTime, slot.endTime)
                    const isClickable = slot.status === 'AVAILABLE' || slot.status === 'FULL' || slot.status === 'AVAILABILITY_WINDOW' || slot.isUserRegistered
                    const showTitle = height >= 30
                    const dragging = isBeingDragged(slot.id)
                    const isCut = cutSlotId === slot.id
                    const isPast = slot.status === 'PAST'
                    const isDraggable = isAdmin && !isPast
                    const isPending = pendingSlotId === slot.id

                    return (
                      <div
                        key={slot.id}
                        className={clsx(
                          'group absolute left-1 right-1 rounded border overflow-hidden transition-colors z-10',
                          isPending
                            ? 'bg-red-600/40 border-red-400/70 text-red-200'
                            : getSlotColors(slot.status),
                          isDraggable && !dragging && 'cursor-grab',
                          dragging && 'opacity-30 cursor-grabbing',
                          isCut && 'ring-2 ring-dashed ring-amber-400 opacity-60',
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
                                ? t('day.fullWaitlist')
                                : slot.eventTitle || getStatusLabel(slot.status, t)}
                            </div>
                          )}
                          {slot.isUserRegistered && (
                            <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 bg-primary-400 rounded-full" />
                          )}
                        </button>

                        {/* Admin action buttons */}
                        {isAdmin && !isPast && (
                          <div
                            data-admin-action
                            className={clsx(
                              'absolute top-0.5 right-0.5 flex gap-0.5 z-20',
                              !isPending && 'opacity-0 group-hover:opacity-100'
                            )}
                          >
                            {isPending ? (
                              <>
                                <button
                                  data-admin-action
                                  onClick={(e) => { e.stopPropagation(); onConfirmSlotMove?.(slot.id) }}
                                  className="p-0.5 rounded bg-green-900/80 text-green-300 hover:text-green-100 transition-colors"
                                  title="Zatwierdź"
                                >
                                  <Check className="w-3 h-3" />
                                </button>
                                <button
                                  data-admin-action
                                  onClick={(e) => { e.stopPropagation(); onCancelSlotMove?.(slot.id) }}
                                  className="p-0.5 rounded bg-red-900/80 text-red-300 hover:text-red-100 transition-colors"
                                  title="Anuluj"
                                >
                                  <X className="w-3 h-3" />
                                </button>
                              </>
                            ) : (
                              <>
                                {!slot.isAvailabilityWindow && slot.currentParticipants > 0 && onNotifyParticipants && (
                                  <button
                                    data-admin-action
                                    onClick={(e) => { e.stopPropagation(); onNotifyParticipants(slot.id) }}
                                    className="p-0.5 rounded bg-dark-900/70 text-dark-300 hover:text-amber-300 transition-colors"
                                    title="Powiadom uczestników"
                                  >
                                    <Bell className="w-2.5 h-2.5" />
                                  </button>
                                )}
                                {onSlotCut && (
                                  <button
                                    data-admin-action
                                    onClick={(e) => { e.stopPropagation(); onSlotCut(slot, day.date) }}
                                    className="p-0.5 rounded bg-dark-900/70 text-dark-300 hover:text-amber-300 transition-colors"
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
