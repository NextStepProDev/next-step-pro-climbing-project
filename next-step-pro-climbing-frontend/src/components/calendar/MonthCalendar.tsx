import { useMemo } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isToday, isBefore, startOfDay } from 'date-fns'
import { pl } from 'date-fns/locale'
import clsx from 'clsx'
import type { DaySummary, EventSummary } from '../../types'

interface MonthCalendarProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
  days: DaySummary[]
  events: EventSummary[]
  onDayClick: (date: string) => void
}

const WEEKDAYS = ['Pon', 'Wt', 'Śr', 'Czw', 'Pt', 'Sob', 'Nd']

export function MonthCalendar({ currentMonth, onMonthChange, days, events, onDayClick }: MonthCalendarProps) {
  const calendarDays = useMemo(() => {
    const start = startOfMonth(currentMonth)
    const end = endOfMonth(currentMonth)
    const daysInMonth = eachDayOfInterval({ start, end })

    // Get the day of week for the first day (0 = Sunday, adjust for Monday start)
    let startDayOfWeek = start.getDay()
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1

    // Create padding for days before month starts
    const paddingBefore = Array(startDayOfWeek).fill(null)

    return [...paddingBefore, ...daysInMonth]
  }, [currentMonth])

  const dayDataMap = useMemo(() => {
    const map = new Map<string, DaySummary>()
    days.forEach((day) => map.set(day.date, day))
    return map
  }, [days])

  const dayEventsMap = useMemo(() => {
    const map = new Map<string, EventSummary[]>()
    events.forEach((event) => {
      const start = new Date(event.startDate)
      const end = new Date(event.endDate)
      for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
        const key = format(d, 'yyyy-MM-dd')
        const list = map.get(key) || []
        list.push(event)
        map.set(key, list)
      }
    })
    return map
  }, [events])

  const goToPreviousMonth = () => {
    const newDate = new Date(currentMonth)
    newDate.setMonth(newDate.getMonth() - 1)
    onMonthChange(newDate)
  }

  const goToNextMonth = () => {
    const newDate = new Date(currentMonth)
    newDate.setMonth(newDate.getMonth() + 1)
    onMonthChange(newDate)
  }

  return (
    <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-dark-800">
        <button
          onClick={goToPreviousMonth}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-semibold text-dark-100 capitalize">
          {format(currentMonth, 'LLLL yyyy', { locale: pl })}
        </h2>
        <button
          onClick={goToNextMonth}
          className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Weekday headers */}
      <div className="grid grid-cols-7 border-b border-dark-800">
        {WEEKDAYS.map((day) => (
          <div
            key={day}
            className="py-2 text-center text-sm font-medium text-dark-500"
          >
            {day}
          </div>
        ))}
      </div>

      {/* Calendar grid */}
      <div className="grid grid-cols-7">
        {calendarDays.map((day, index) => {
          if (!day) {
            return <div key={`empty-${index}`} className="aspect-square bg-dark-950/50" />
          }

          const dateString = format(day, 'yyyy-MM-dd')
          const dayData = dayDataMap.get(dateString)
          const dayEvents = dayEventsMap.get(dateString) || []
          const isPast = isBefore(day, startOfDay(new Date()))
          const hasSlots = dayData && dayData.totalSlots > 0
          const hasUserReservation = dayData?.hasUserReservation
          const hasEvents = dayEvents.length > 0
          const isClickable = !isPast && (hasSlots || hasEvents)

          return (
            <button
              key={dateString}
              onClick={() => isClickable && onDayClick(dateString)}
              disabled={!isClickable}
              className={clsx(
                'aspect-square p-1 sm:p-2 border-b border-r border-dark-800 transition-colors relative',
                !isSameMonth(day, currentMonth) && 'opacity-30',
                isPast && 'opacity-40 cursor-not-allowed',
                isClickable && 'hover:bg-dark-800 cursor-pointer',
                !isClickable && 'cursor-default',
                hasEvents && !isPast && 'bg-primary-500/10'
              )}
            >
              <div
                className={clsx(
                  'text-sm font-medium mb-0.5',
                  isToday(day) && 'text-primary-400',
                  !isToday(day) && 'text-dark-300'
                )}
              >
                {format(day, 'd')}
              </div>

              {dayEvents.length > 0 && !isPast && dayEvents.map((event) => (
                <div key={event.id} className="text-[10px] leading-tight text-primary-400 font-medium truncate">
                  {event.title}{' '}
                  <span className={clsx(
                    event.currentParticipants >= event.maxParticipants ? 'text-amber-400' : 'text-primary-300'
                  )}>
                    {event.currentParticipants}/{event.maxParticipants}
                  </span>
                </div>
              ))}

              {dayData && dayData.availableSlots > 0 && !isPast ? (
                <div className="text-xs text-primary-400 font-medium">
                  {dayData.availableSlots} wolne
                </div>
              ) : dayData && dayData.totalSlots > 0 && dayData.availableSlots === 0 && !hasEvents && !isPast ? (
                <div className="text-xs text-amber-400 font-medium">
                  Brak miejsc
                </div>
              ) : null}

              {hasUserReservation && (
                <div className="absolute top-1 right-1 w-2 h-2 bg-primary-500 rounded-full" />
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
