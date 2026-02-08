import { useMemo } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isToday, isBefore, startOfDay } from 'date-fns'
import { pl } from 'date-fns/locale'
import clsx from 'clsx'
import type { DaySummary } from '../../types'

interface MonthCalendarProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
  days: DaySummary[]
  onDayClick: (date: string) => void
}

const WEEKDAYS = ['Pon', 'Wt', 'Śr', 'Czw', 'Pt', 'Sob', 'Nd']

export function MonthCalendar({ currentMonth, onMonthChange, days, onDayClick }: MonthCalendarProps) {
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
          const isPast = isBefore(day, startOfDay(new Date()))
          const hasSlots = dayData && dayData.totalSlots > 0
          const hasAvailable = dayData && dayData.availableSlots > 0
          const hasUserReservation = dayData?.hasUserReservation

          return (
            <button
              key={dateString}
              onClick={() => !isPast && hasSlots && onDayClick(dateString)}
              disabled={isPast || !hasSlots}
              className={clsx(
                'aspect-square p-1 sm:p-2 border-b border-r border-dark-800 transition-colors relative',
                !isSameMonth(day, currentMonth) && 'opacity-30',
                isPast && 'opacity-40 cursor-not-allowed',
                hasSlots && !isPast && 'hover:bg-dark-800 cursor-pointer',
                !hasSlots && 'cursor-default'
              )}
            >
              <div
                className={clsx(
                  'text-sm font-medium mb-1',
                  isToday(day) && 'text-primary-400',
                  !isToday(day) && 'text-dark-300'
                )}
              >
                {format(day, 'd')}
              </div>

              {hasSlots && (
                <div className="space-y-0.5">
                  {hasAvailable && !isPast ? (
                    <div className="text-xs text-green-400 font-medium">
                      {dayData.availableSlots} wolne
                    </div>
                  ) : hasSlots && !isPast ? (
                    <div className="text-xs text-amber-400 font-medium">
                      Brak miejsc
                    </div>
                  ) : null}
                </div>
              )}

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
