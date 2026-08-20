import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Ban, ChevronLeft, ChevronRight, NotebookPen } from 'lucide-react'
import { format, startOfMonth, endOfMonth, eachDayOfInterval, isSameMonth, isBefore, startOfDay } from 'date-fns'
import clsx from 'clsx'
import type { DaySummary, EventSummary } from '../../types'
import type { EventColorMap } from '../../utils/events'
import { getEventColorByIndex, pluralizeTraining } from '../../utils/events'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { useAuth } from '../../context/AuthContext'
import { isTodayInWarsaw, nowInWarsaw, parseCalendarDate } from '../../utils/calendarDate'
import type { NoteMarks } from '../admin/useNoteMarks'

// A month cell is ~50 px wide on a phone, so a full "18:00–20:00" does not fit. Whole hours drop
// their ":00" the way printed timetables do; a half hour keeps its minutes, because that is the
// part someone would get wrong.
function compactTime(time: string): string {
  const [hours, minutes] = time.split(':')
  return minutes === '00' ? hours : `${hours}:${minutes}`
}

interface MonthCalendarProps {
  currentMonth: Date
  onMonthChange: (date: Date) => void
  days: DaySummary[]
  events: EventSummary[]
  onDayClick: (date: string) => void
  allDaysClickable?: boolean
  eventColorMap: EventColorMap
  // Admin only. Undefined for everybody else, so the marker cannot render by accident.
  noteMarks?: NoteMarks
}

export function MonthCalendar({ currentMonth, onMonthChange, days, events, onDayClick, allDaysClickable, eventColorMap, noteMarks }: MonthCalendarProps) {
  const { t } = useTranslation('calendar')
  const { isAuthenticated } = useAuth()
  const locale = useDateLocale()

  const weekdays = [
    t('weekdays.mon'), t('weekdays.tue'), t('weekdays.wed'),
    t('weekdays.thu'), t('weekdays.fri'), t('weekdays.sat'), t('weekdays.sun'),
  ]

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
      const start = parseCalendarDate(event.startDate)
      const end = parseCalendarDate(event.endDate)
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
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-surface-800">
        <button
          aria-label={t('month.previousMonth')}
          onClick={goToPreviousMonth}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>
        <h2 className="text-lg font-semibold text-surface-100 capitalize">
          {format(currentMonth, 'LLLL yyyy', { locale })}
        </h2>
        <button
          aria-label={t('month.nextMonth')}
          onClick={goToNextMonth}
          className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors"
        >
          <ChevronRight className="w-5 h-5" />
        </button>
      </div>

      {/* Weekday headers */}
      <div className="grid grid-cols-7 border-b border-surface-800">
        {weekdays.map((day) => (
          <div
            key={day}
            className="py-2 text-center text-sm font-medium text-surface-500"
          >
            {day}
          </div>
        ))}
      </div>

      {/* Calendar grid */}
      <div className="grid grid-cols-7">
        {calendarDays.map((day, index) => {
          if (!day) {
            return <div key={`empty-${index}`} className="aspect-square bg-surface-950/50" />
          }

          const dateString = format(day, 'yyyy-MM-dd')
          const dayData = dayDataMap.get(dateString)
          const dayEvents = dayEventsMap.get(dateString) || []
          const isPast = isBefore(day, startOfDay(nowInWarsaw()))
          const hasAvailabilityWindow = dayData?.hasAvailabilityWindow ?? false
          const unavailableRanges = dayData?.unavailableRanges ?? []
          const hasUserReservation = dayData?.hasUserReservation
          const hasEvents = dayEvents.length > 0
          // Future days are clickable even when EMPTY — they open the day view with the "Propose a time"
          // CTA (without this, the only request entry point from an empty day was the week view).
          const isClickable = allDaysClickable || !isPast
          // A note on any slot that day, or on any event covering it. Matched by date for slots
          // because the cell has no slot ids; by id for events, which it does hold.
          const hasNote = !!noteMarks && (
            noteMarks.dates.has(dateString) || dayEvents.some((e) => noteMarks.events.has(e.id))
          )

          return (
            <button
              key={dateString}
              onClick={() => isClickable && onDayClick(dateString)}
              disabled={!isClickable}
              className={clsx(
                // overflow-hidden, because a cell is a grid track: a label that does not fit used to
                // spill over the neighbouring day and read as if it belonged there.
                'aspect-square p-0.5 sm:p-2 border-b border-r border-surface-800 transition-colors relative overflow-hidden',
                !isSameMonth(day, currentMonth) && 'opacity-40',
                isPast && 'opacity-50 cursor-not-allowed',
                isClickable && 'hover:bg-surface-800 cursor-pointer',
                !isClickable && 'cursor-default',
                hasEvents && !isPast && (
                  dayEvents.every(e => e.eventType === 'UNAVAILABLE') ? 'bg-slate-500/10'
                    : dayEvents.every(e => e.eventType === 'CONTACT_DAY') ? 'bg-indigo-500/10'
                      : 'bg-primary-500/10'
                )
                // No tint for unavailable SLOTS: an absence takes a few hours, and colouring the
                // whole cell told people the entire day was off. The hours ride in their own bar
                // below. A whole day off is an UNAVAILABLE event, tinted by the branch above.
              )}
            >
              <div
                className={clsx(
                  'text-sm font-medium mb-0.5',
                  isTodayInWarsaw(day) && 'text-primary-400',
                  !isTodayInWarsaw(day) && 'text-surface-300'
                )}
              >
                {format(day, 'd')}
              </div>

              {dayEvents.length > 0 && dayEvents.map((event) => {
                const color = eventColorMap.get(event.id) ?? getEventColorByIndex(event.id, event.eventType, event.currentParticipants >= event.maxParticipants)
                return (
                  <div key={event.id} className={clsx(
                    "text-[10px] sm:text-[11px] leading-snug font-medium truncate rounded border px-0.5 sm:px-1 py-0 sm:py-0.5 mb-0.5",
                    color.barBg, color.barBorder, color.barText
                  )}>
                    {event.title}
                  </div>
                )
              })}

              {dayData && dayData.availableSlots > 0 && !isPast ? (
                <div className="text-xs text-green-300 font-medium truncate">
                  {pluralizeTraining(dayData.availableSlots)}
                </div>
              ) : dayData && dayData.totalSlots > 0 && dayData.availableSlots === 0 && dayData.hasReservedSeats && !isAuthenticated && !isPast ? (
                <div className="text-xs text-violet-300 font-medium leading-tight truncate">
                  {t('month.invitedOnly')}
                </div>
              ) : dayData && dayData.totalSlots > 0 && dayData.availableSlots === 0 && !hasEvents && !isPast ? (
                <div className="text-xs text-amber-400/80 font-medium truncate">
                  {t('noSpots')}
                </div>
              ) : null}

              {hasAvailabilityWindow && !isPast && (
                <div title={t('day.callToBook')} className="text-[10px] text-teal-400 font-medium leading-tight truncate">
                  {t('day.callToBook')}
                </div>
              )}

              {!isPast && unavailableRanges.map((range) => {
                const from = range.startTime.slice(0, 5)
                const to = range.endTime.slice(0, 5)
                return (
                  <div
                    key={`${from}-${to}`}
                    title={`${t('day.unavailable')} ${from}–${to}`}
                    className="flex items-center gap-0.5 text-[10px] sm:text-[11px] leading-snug font-medium truncate rounded border px-0.5 sm:px-1 py-0 sm:py-0.5 mb-0.5 bg-slate-500/10 border-slate-500/30 text-slate-300"
                  >
                    {/* A phone cell is too narrow for icon AND hours — the hours are the part that
                        carries information, so the icon goes and the strikethrough does its job. */}
                    <Ban className="hidden sm:block w-2.5 h-2.5 shrink-0" aria-hidden="true" />
                    <span className="sr-only">{t('day.unavailable')} </span>
                    <span className="truncate line-through decoration-slate-500">{compactTime(from)}–{compactTime(to)}</span>
                  </div>
                )
              })}

              {hasUserReservation && (
                <div className="absolute top-1 right-1 w-2 h-2 bg-primary-500 rounded-full" />
              )}

              {/* Absolutely positioned like the reservation dot above, and for the same reason:
                  the cell is a grid track with overflow-hidden, so anything added to the flow
                  competes with labels that are already truncating. Bottom-right is the only
                  corner nothing else uses. */}
              {hasNote && (
                <NotebookPen
                  className="absolute bottom-1 right-1 w-3 h-3 text-amber-500"
                  aria-label={t('month.hasPrivateNote')}
                />
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}
