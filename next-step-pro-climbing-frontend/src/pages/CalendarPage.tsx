import { useState, useCallback, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { format } from 'date-fns'
import { calendarApi } from '../api/client'
import { MonthCalendar } from '../components/calendar/MonthCalendar'
import { DayView } from '../components/calendar/DayView'
import { SlotDetailModal } from '../components/calendar/SlotDetailModal'
import { EventSignupModal } from '../components/calendar/EventSignupModal'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import type { EventSummary } from '../types'

export function CalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [currentMonth, setCurrentMonth] = useState(() => {
    const dateParam = searchParams.get('date')
    return dateParam ? new Date(dateParam) : new Date()
  })
  const [selectedDate, setSelectedDate] = useState<string | null>(
    searchParams.get('date')
  )
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null)
  const [selectedEvent, setSelectedEvent] = useState<EventSummary | null>(null)
  const eventsRef = useRef<HTMLDivElement>(null)

  const yearMonth = format(currentMonth, 'yyyy-MM')

  const { data: monthData, isLoading: monthLoading } = useQuery({
    queryKey: ['calendar', 'month', yearMonth],
    queryFn: () => calendarApi.getMonthView(yearMonth),
  })

  const { data: dayData, isLoading: dayLoading } = useQuery({
    queryKey: ['calendar', 'day', selectedDate],
    queryFn: () => calendarApi.getDayView(selectedDate!),
    enabled: !!selectedDate,
    staleTime: 0,
  })

  const { data: slotDetail } = useQuery({
    queryKey: ['slot', selectedSlotId],
    queryFn: () => calendarApi.getSlotDetails(selectedSlotId!),
    enabled: !!selectedSlotId,
  })

  const handleDayClick = useCallback((date: string) => {
    const dayInfo = monthData?.days.find(d => d.date === date)
    const hasSlots = dayInfo && dayInfo.totalSlots > 0

    if (hasSlots) {
      // Day has time slots (trainings or event-linked) - open day view
      setSelectedDate(date)
      setSearchParams({ date })
      return
    }

    // Day has only events (no time slots) - find matching events
    const dayEvents = monthData?.events.filter(e => date >= e.startDate && date <= e.endDate) || []

    if (dayEvents.length === 1) {
      // Single event - open signup modal directly
      setSelectedEvent(dayEvents[0])
    } else if (dayEvents.length > 1) {
      // Multiple events - scroll to events section
      eventsRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [monthData, setSearchParams])

  const handleBackToMonth = useCallback(() => {
    setSelectedDate(null)
    setSearchParams({})
  }, [setSearchParams])

  const handleSlotClick = useCallback((slotId: string) => {
    setSelectedSlotId(slotId)
  }, [])

  const handleModalClose = useCallback(() => {
    setSelectedSlotId(null)
  }, [])

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">Kalendarz</h1>
        <p className="text-dark-400">
          Wybierz dzień, aby zobaczyć dostępne godziny i zapisać się na zajęcia.
        </p>
      </div>

      {monthLoading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : selectedDate && dayData ? (
        <DayView
          date={selectedDate}
          slots={dayData.slots}
          events={dayData.events}
          onBack={handleBackToMonth}
          onSlotClick={handleSlotClick}
          onEventClick={setSelectedEvent}
        />
      ) : monthData ? (
        <>
          <MonthCalendar
            currentMonth={currentMonth}
            onMonthChange={setCurrentMonth}
            days={monthData.days}
            events={monthData.events}
            onDayClick={handleDayClick}
          />

          {/* Events legend */}
          {monthData.events.length > 0 && (
            <div ref={eventsRef} className="mt-6 bg-dark-900 rounded-xl border border-dark-800 p-4">
              <h3 className="text-sm font-medium text-dark-300 mb-3">
                Wydarzenia w tym miesiącu:
              </h3>
              <div className="space-y-2">
                {monthData.events.map((event) => (
                  <div
                    key={event.id}
                    className="flex items-center justify-between text-sm"
                  >
                    <span className="text-dark-100">{event.title}</span>
                    <div className="flex items-center gap-3">
                      <span className={
                        event.currentParticipants >= event.maxParticipants
                          ? 'text-amber-400'
                          : 'text-primary-400'
                      }>
                        {event.currentParticipants}/{event.maxParticipants} miejsc
                      </span>
                      <span className="text-dark-400">
                        {format(new Date(event.startDate), 'dd.MM')}
                        {event.isMultiDay && (
                          <> - {format(new Date(event.endDate), 'dd.MM')}</>
                        )}
                      </span>
                      <button
                        onClick={() => setSelectedEvent(event)}
                        className={
                          event.isUserRegistered
                            ? 'px-3 py-1 text-xs font-medium rounded transition-colors bg-primary-500/20 text-primary-400'
                            : 'px-3 py-1 text-xs font-medium rounded transition-colors bg-primary-600 text-white hover:bg-primary-500'
                        }
                      >
                        {event.isUserRegistered ? 'Zapisany' : 'Zapisz się'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      ) : null}

      {dayLoading && (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      )}

      <SlotDetailModal
        slot={slotDetail ?? null}
        isOpen={!!selectedSlotId}
        onClose={handleModalClose}
      />

      <EventSignupModal
        event={selectedEvent}
        isOpen={!!selectedEvent}
        onClose={() => setSelectedEvent(null)}
      />
    </div>
  )
}
