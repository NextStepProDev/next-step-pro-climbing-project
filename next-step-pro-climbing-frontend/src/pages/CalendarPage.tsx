import { useState, useCallback, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { format } from "date-fns";
import { calendarApi } from "../api/client";
import { MonthCalendar } from "../components/calendar/MonthCalendar";
import { DayView } from "../components/calendar/DayView";
import { SlotDetailModal } from "../components/calendar/SlotDetailModal";
import { EventSignupModal } from "../components/calendar/EventSignupModal";
import { LoadingSpinner } from "../components/ui/LoadingSpinner";
import { QueryError } from "../components/ui/QueryError";
import { formatAvailability, getEventColor } from "../utils/events";
import type { EventSummary } from "../types";

export function CalendarPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [currentMonth, setCurrentMonth] = useState(() => {
    const dateParam = searchParams.get("date");
    return dateParam ? new Date(dateParam) : new Date();
  });
  const [selectedDate, setSelectedDate] = useState<string | null>(
    searchParams.get("date"),
  );
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventSummary | null>(null);
  const eventsRef = useRef<HTMLDivElement>(null);

  const yearMonth = format(currentMonth, "yyyy-MM");

  const {
    data: monthData,
    isLoading: monthLoading,
    isError: monthError,
    error: monthErrorObj,
    refetch: refetchMonth,
  } = useQuery({
    queryKey: ["calendar", "month", yearMonth],
    queryFn: () => calendarApi.getMonthView(yearMonth),
  });

  const {
    data: dayData,
    isLoading: dayLoading,
    isError: dayError,
    error: dayErrorObj,
    refetch: refetchDay,
  } = useQuery({
    queryKey: ["calendar", "day", selectedDate],
    queryFn: () => calendarApi.getDayView(selectedDate!),
    enabled: !!selectedDate,
    staleTime: 0,
  });

  const { data: slotDetail } = useQuery({
    queryKey: ["slot", selectedSlotId],
    queryFn: () => calendarApi.getSlotDetails(selectedSlotId!),
    enabled: !!selectedSlotId,
  });

  const handleDayClick = useCallback(
    (date: string) => {
      const dayInfo = monthData?.days.find((d) => d.date === date);
      const hasSlots = dayInfo && dayInfo.totalSlots > 0;

      if (hasSlots) {
        setSelectedDate(date);
        setSearchParams({ date });
        return;
      }

      const dayEvents =
        monthData?.events.filter(
          (e) => date >= e.startDate && date <= e.endDate,
        ) || [];

      if (dayEvents.length === 1) {
        setSelectedEvent(dayEvents[0]);
      } else if (dayEvents.length > 1) {
        eventsRef.current?.scrollIntoView({ behavior: "smooth" });
      }
    },
    [monthData, setSearchParams],
  );

  const handleBackToMonth = useCallback(() => {
    setSelectedDate(null);
    setSearchParams({});
  }, [setSearchParams]);

  const handleSlotClick = useCallback((slotId: string) => {
    setSelectedSlotId(slotId);
  }, []);

  const handleModalClose = useCallback(() => {
    setSelectedSlotId(null);
  }, []);

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">Kalendarz</h1>
        <p className="text-dark-400">
          Wybierz dzień, aby zobaczyć dostępne godziny i zapisać się na zajęcia.
        </p>

        {/* PROMOCJA */}
        <div className="mt-4 inline-block bg-amber-500/10 border border-amber-500/30 rounded-lg px-4 py-3">
          <p className="text-amber-400 font-semibold text-sm">
            🔥 Promocja tylko do końca lutego!!
          </p>
          <p className="text-amber-300/80 text-xs mt-1">
            Kup 2 treningi i zapłać jak za dwa!!
          </p>
        </div>
      </div>

      {monthLoading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : monthError ? (
        <QueryError error={monthErrorObj} onRetry={() => refetchMonth()} />
      ) : selectedDate && dayError ? (
        <QueryError error={dayErrorObj} onRetry={() => refetchDay()} />
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
            <div
              ref={eventsRef}
              className="mt-6 bg-dark-900 rounded-xl border border-dark-800 p-4"
            >
              <h3 className="text-sm font-medium text-dark-300 mb-3">
                Wydarzenia w tym miesiącu:
              </h3>

              <div className="space-y-2">
                {monthData.events.map((event, index) => {
                  const { label, badgeClass } = formatAvailability(event);
                  const color = getEventColor(index);

                  return (
                    <div
                      key={event.id}
                      className="flex items-center justify-between text-sm bg-dark-800/40 rounded-lg px-3 py-2"
                    >
                      <span className="flex items-center gap-2 text-dark-100 font-medium">
                        <span
                          className={`w-2.5 h-2.5 rounded-full shrink-0 ${color.dot}`}
                        />
                        {event.title}
                      </span>

                      <div className="flex items-center gap-3">
                        {/* availability badge */}
                        <span
                          className={`px-2 py-1 rounded-full text-xs font-medium ${badgeClass}`}
                        >
                          {label}
                        </span>

                        {/* date */}
                        <span className="text-dark-400 text-xs">
                          {format(new Date(event.startDate), "dd.MM")}
                          {event.isMultiDay && (
                            <> - {format(new Date(event.endDate), "dd.MM")}</>
                          )}
                        </span>

                        {/* button */}
                        {!event.enrollmentOpen && !event.isUserRegistered ? (
                          <button
                            onClick={() => setSelectedEvent(event)}
                            className="px-3 py-1 text-xs font-medium rounded bg-dark-700 text-dark-400 hover:bg-dark-600 transition-colors"
                          >
                            Zadzwoń
                          </button>
                        ) : (
                          <button
                            onClick={() => setSelectedEvent(event)}
                            className={
                              event.isUserRegistered
                                ? "px-3 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400"
                                : "px-3 py-1 text-xs font-medium rounded bg-primary-600 text-white hover:bg-primary-500 transition-colors"
                            }
                          >
                            {event.isUserRegistered ? "Zapisany" : "Zapisz się"}
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
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
  );
}
