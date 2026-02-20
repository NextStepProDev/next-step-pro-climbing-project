import { useMemo, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, isToday, isBefore, startOfDay } from 'date-fns'
import clsx from 'clsx'
import type { WeekDay, TimeSlot, EventSummary } from '../../types'
import { useDateLocale } from '../../utils/dateFnsLocale'

const HOUR_HEIGHT = 60
const START_HOUR = 7
const END_HOUR = 22
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
    default:
      return ''
  }
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
}: WeekCalendarProps) {
  const { t } = useTranslation('calendar')
  const locale = useDateLocale()
  const scrollRef = useRef<HTMLDivElement>(null)

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
                    <div className="w-1.5 h-1.5 bg-primary-500 rounded-full mx-auto mt-0.5" />
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
                  className="border-b border-dark-800/50 text-right pr-2 text-xs text-dark-500"
                  style={{ height: HOUR_HEIGHT }}
                >
                  <span className="relative -top-2">{`${hour}:00`}</span>
                </div>
              ))}
            </div>

            {/* Day columns */}
            {days.map((day) => {
              const date = new Date(day.date)
              const today = isToday(date)
              const past = isBefore(date, startOfDay(new Date()))
              const dayEvents = dayEventsMap.get(day.date) || []

              return (
                <div
                  key={day.date}
                  className={clsx(
                    'relative border-l border-dark-800',
                    today && 'bg-primary-500/5',
                    past && 'opacity-40',
                  )}
                  style={{ height: TOTAL_HOURS * HOUR_HEIGHT }}
                >
                  {/* Hour grid lines */}
                  {hours.map((hour) => (
                    <div
                      key={hour}
                      className="absolute w-full border-b border-dark-800/50"
                      style={{ top: (hour - START_HOUR) * HOUR_HEIGHT, height: HOUR_HEIGHT }}
                    />
                  ))}

                  {/* Event bars at top area */}
                  {dayEvents.map((event) => (
                    <button
                      key={event.id}
                      onClick={() => onEventClick(event)}
                      className="absolute left-0.5 right-0.5 top-0 z-20 px-1 py-0.5 text-[10px] leading-tight font-medium rounded bg-primary-600/40 border border-primary-500/40 text-primary-300 truncate hover:bg-primary-600/50 transition-colors cursor-pointer"
                    >
                      {event.title}
                    </button>
                  ))}

                  {/* Slots */}
                  {day.slots.map((slot: TimeSlot) => {
                    const { top, height } = getSlotPosition(slot.startTime, slot.endTime)
                    const isClickable = slot.status === 'AVAILABLE' || slot.status === 'FULL' || slot.isUserRegistered
                    const showTitle = height >= 45

                    return (
                      <button
                        key={slot.id}
                        onClick={() => isClickable ? onSlotClick(slot.id) : undefined}
                        disabled={!isClickable}
                        className={clsx(
                          'absolute left-1 right-1 rounded border px-1.5 py-0.5 text-left overflow-hidden transition-colors z-10',
                          getSlotColors(slot.status),
                          isClickable && 'cursor-pointer',
                          !isClickable && 'cursor-default',
                          slot.isUserRegistered && 'ring-1 ring-primary-400',
                        )}
                        style={{ top, height }}
                      >
                        <div className="text-[11px] font-semibold leading-tight truncate">
                          {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                        </div>
                        {showTitle && (
                          <div className="text-[10px] leading-tight truncate opacity-80">
                            {slot.eventTitle || getStatusLabel(slot.status, t)}
                          </div>
                        )}
                        {slot.isUserRegistered && (
                          <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 bg-primary-400 rounded-full" />
                        )}
                      </button>
                    )
                  })}
                </div>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
