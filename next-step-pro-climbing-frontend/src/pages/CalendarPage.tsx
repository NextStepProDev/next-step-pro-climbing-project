import { useState, useCallback, useRef } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSearchParams, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { format, startOfWeek, addWeeks, subWeeks } from "date-fns";
import { calendarApi, reservationApi, adminApi } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { MonthCalendar } from "../components/calendar/MonthCalendar";
import { WeekCalendar } from "../components/calendar/WeekCalendar";
import { DayView } from "../components/calendar/DayView";
import { SlotDetailModal } from "../components/calendar/SlotDetailModal";
import { EventSignupModal } from "../components/calendar/EventSignupModal";
import { CreateSlotModal } from "../components/calendar/CreateSlotModal";
import { LoadingSpinner } from "../components/ui/LoadingSpinner";
import { QueryError } from "../components/ui/QueryError";
import { Phone, Mail, ExternalLink, Scissors, X, Bell } from "lucide-react";
import { formatAvailability, getEventColorByIndex } from "../utils/events";
import type { EventSummary, TimeSlot } from "../types";

export function CalendarPage() {
  const { t } = useTranslation('calendar');
  const { isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [viewMode, setViewMode] = useState<'month' | 'week'>(() => {
    return searchParams.get("view") === 'week' ? 'week' : 'month';
  });
  const [currentMonth, setCurrentMonth] = useState(() => {
    const dateParam = searchParams.get("date");
    return dateParam ? new Date(dateParam) : new Date();
  });
  const [currentWeekStart, setCurrentWeekStart] = useState(() => {
    return startOfWeek(new Date(), { weekStartsOn: 1 });
  });
  const [selectedDate, setSelectedDate] = useState<string | null>(
    searchParams.get("view") === 'week' ? null : searchParams.get("date"),
  );
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
  const [selectedEvent, setSelectedEvent] = useState<EventSummary | null>(null);
  const [showCreateSlotModal, setShowCreateSlotModal] = useState(false);
  const [cutSlot, setCutSlot] = useState<{ id: string; date: string; startTime: string; endTime: string } | null>(null);
  const [notifyToast, setNotifyToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const lastSlotMoveRef = useRef<Map<string, { previousDate: string; previousStartTime: string; previousEndTime: string }>>(new Map());
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

  const weekStartString = format(currentWeekStart, 'yyyy-MM-dd');

  const {
    data: weekData,
    isLoading: weekLoading,
    isError: weekError,
    error: weekErrorObj,
    refetch: refetchWeek,
  } = useQuery({
    queryKey: ["calendar", "week", weekStartString],
    queryFn: () => calendarApi.getWeekView(weekStartString),
    enabled: viewMode === 'week',
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

      if (hasSlots || isAdmin) {
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
    [monthData, setSearchParams, isAdmin],
  );

  const handleSlotClick = useCallback((slotId: string) => {
    setSelectedSlotId(slotId);
  }, []);

  const handleViewModeChange = useCallback((mode: 'month' | 'week') => {
    setViewMode(mode);
    setSelectedDate(null);
    if (mode === 'week') {
      setSearchParams({ view: 'week' });
    } else {
      setSearchParams({});
    }
  }, [setSearchParams]);

  const handlePrevWeek = useCallback(() => {
    setCurrentWeekStart(prev => subWeeks(prev, 1));
  }, []);

  const handleNextWeek = useCallback(() => {
    setCurrentWeekStart(prev => addWeeks(prev, 1));
  }, []);

  const handleTodayWeek = useCallback(() => {
    setCurrentWeekStart(startOfWeek(new Date(), { weekStartsOn: 1 }));
  }, []);

  const handleWeekDayClick = useCallback((date: string) => {
    setSelectedDate(date);
    setSearchParams({ view: 'week', date });
  }, [setSearchParams]);

  const handleBackFromDay = useCallback(() => {
    setSelectedDate(null);
    if (viewMode === 'week') {
      setSearchParams({ view: 'week' });
    } else {
      setSearchParams({});
    }
  }, [viewMode, setSearchParams]);

  const handleModalClose = useCallback(() => {
    setSelectedSlotId(null);
  }, []);

  const moveSlotMutation = useMutation({
    mutationFn: ({ slotId, date, startTime, endTime }: { slotId: string; date: string; startTime: string; endTime: string }) =>
      adminApi.updateTimeSlot(slotId, { date, startTime, endTime, sendNotifications: false }),
    onSuccess: (data, variables) => {
      if (data.currentParticipants === 0) {
        lastSlotMoveRef.current.delete(variables.slotId);
      }
      void queryClient.invalidateQueries({ queryKey: ['calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'slots'] });
    },
    onError: (_err, variables) => {
      lastSlotMoveRef.current.delete(variables.slotId);
    },
  });

  const notifyParticipantsMutation = useMutation({
    mutationFn: (slotId: string) => adminApi.notifySlotParticipants(slotId, lastSlotMoveRef.current.get(slotId)),
    onSuccess: (data, slotId) => {
      lastSlotMoveRef.current.delete(slotId);
      const msg = data.notifiedCount === 0
        ? 'Brak uczestników z włączonymi powiadomieniami'
        : `Wysłano ${data.notifiedCount} ${data.notifiedCount === 1 ? 'powiadomienie' : data.notifiedCount < 5 ? 'powiadomienia' : 'powiadomień'}`;
      setNotifyToast({ type: 'success', message: msg });
      setTimeout(() => setNotifyToast(null), 4000);
    },
    onError: () => {
      setNotifyToast({ type: 'error', message: 'Nie udało się wysłać powiadomień' });
      setTimeout(() => setNotifyToast(null), 4000);
    },
  });

  const handleSlotDrop = useCallback((slotId: string, newDate: string, newStartTime: string, newEndTime: string, oldDate: string, oldStartTime: string, oldEndTime: string) => {
    if (!lastSlotMoveRef.current.has(slotId)) {
      lastSlotMoveRef.current.set(slotId, { previousDate: oldDate, previousStartTime: oldStartTime, previousEndTime: oldEndTime });
    }
    moveSlotMutation.mutate({ slotId, date: newDate, startTime: newStartTime, endTime: newEndTime });
  }, [moveSlotMutation]);

  const handleSlotCut = useCallback((slot: TimeSlot, date: string) => {
    setCutSlot({ id: slot.id, date, startTime: slot.startTime, endTime: slot.endTime });
  }, []);

  const handleColumnClick = useCallback((date: string, startTime: string) => {
    if (!cutSlot) return;
    const [sh, sm] = cutSlot.startTime.split(':').map(Number);
    const [eh, em] = cutSlot.endTime.split(':').map(Number);
    const durationMin = (eh * 60 + em) - (sh * 60 + sm);
    const [clickH, clickM] = startTime.split(':').map(Number);
    const endAbsMin = clickH * 60 + clickM + durationMin;
    const endH = Math.floor(endAbsMin / 60);
    const endM = endAbsMin % 60;
    const endTime = `${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}`;
    if (!lastSlotMoveRef.current.has(cutSlot.id)) {
      lastSlotMoveRef.current.set(cutSlot.id, { previousDate: cutSlot.date, previousStartTime: cutSlot.startTime, previousEndTime: cutSlot.endTime });
    }
    moveSlotMutation.mutate({ slotId: cutSlot.id, date, startTime, endTime });
    setCutSlot(null);
  }, [cutSlot, moveSlotMutation]);

  const cancelEventMutation = useMutation({
    mutationFn: (eventId: string) => reservationApi.cancelForEvent(eventId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['calendar', 'day', selectedDate] });
      void queryClient.invalidateQueries({ queryKey: ['reservations'] });
    },
  });

  return (
    <div className={`mx-auto px-4 sm:px-6 lg:px-8 py-8 ${viewMode === 'week' && !selectedDate ? 'max-w-6xl' : 'max-w-4xl'}`}>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">{t('title')}</h1>
        <p className="text-dark-400">
          {t('subtitle')}
        </p>

        {/* PROMOCJA */}
        <div className="mt-4 inline-block bg-amber-500/10 border border-amber-500/30 rounded-lg px-4 py-3">
          <p className="text-amber-400 font-semibold text-sm">
            {t('promo.title')}
          </p>
          <p className="text-amber-300/80 text-xs mt-1">
            {t('promo.description')}
          </p>
        </div>

        {/* View mode toggle */}
        {!selectedDate && (
          <div className="mt-4 flex gap-1 bg-dark-800 rounded-lg p-1 w-fit">
            <button
              onClick={() => handleViewModeChange('month')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                viewMode === 'month'
                  ? 'bg-primary-600 text-white'
                  : 'text-dark-400 hover:text-dark-200'
              }`}
            >
              {t('viewMode.month')}
            </button>
            <button
              onClick={() => handleViewModeChange('week')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                viewMode === 'week'
                  ? 'bg-primary-600 text-white'
                  : 'text-dark-400 hover:text-dark-200'
              }`}
            >
              {t('viewMode.week')}
            </button>
          </div>
        )}
      </div>

      {/* Day view (shared between month and week modes) */}
      {selectedDate && dayError ? (
        <QueryError error={dayErrorObj} onRetry={() => refetchDay()} />
      ) : selectedDate && dayData ? (
        <DayView
          date={selectedDate}
          slots={dayData.slots}
          events={dayData.events}

          onBack={handleBackFromDay}
          onSlotClick={handleSlotClick}
          onEventClick={setSelectedEvent}
          onCancelEvent={(id) => cancelEventMutation.mutate(id)}
          onAddSlot={isAdmin ? () => setShowCreateSlotModal(true) : undefined}
        />
      ) : viewMode === 'week' ? (
        weekLoading ? (
          <div className="flex justify-center py-12">
            <LoadingSpinner size="lg" />
          </div>
        ) : weekError ? (
          <QueryError error={weekErrorObj} onRetry={() => refetchWeek()} />
        ) : weekData ? (
          <>
            {/* Notify toast */}
            {notifyToast && (
              <div className={`mb-3 flex items-center gap-3 px-4 py-2.5 rounded-lg border ${
                notifyToast.type === 'success'
                  ? 'bg-primary-500/10 border-primary-500/30 text-primary-300'
                  : 'bg-red-500/10 border-red-500/30 text-red-300'
              }`}>
                <Bell className="w-4 h-4 shrink-0" />
                <span className="text-sm flex-1">{notifyToast.message}</span>
                <button onClick={() => setNotifyToast(null)} className="p-1 opacity-60 hover:opacity-100 transition-opacity">
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* Cut-mode banner */}
            {isAdmin && cutSlot && (
              <div className="mb-3 flex items-center gap-3 px-4 py-2.5 bg-amber-500/10 border border-amber-500/30 rounded-lg">
                <Scissors className="w-4 h-4 text-amber-400 shrink-0" />
                <span className="text-sm text-amber-300 flex-1">
                  Slot <strong>{cutSlot.startTime.slice(0, 5)}–{cutSlot.endTime.slice(0, 5)}</strong> wytnięty — kliknij w wolne miejsce w kalendarzu, aby wkleić
                </span>
                <button
                  onClick={() => setCutSlot(null)}
                  className="p-1 text-amber-400 hover:text-amber-200 transition-colors"
                  title="Anuluj"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            <WeekCalendar
              startDate={weekData.startDate}
              days={weekData.days}
              events={weekData.events}
              onPrevWeek={handlePrevWeek}
              onNextWeek={handleNextWeek}
              onToday={handleTodayWeek}
              onSlotClick={handleSlotClick}
              onEventClick={setSelectedEvent}
              onDayClick={handleWeekDayClick}
              isAdmin={isAdmin}
              onSlotDrop={isAdmin ? handleSlotDrop : undefined}
              onSlotCut={isAdmin ? handleSlotCut : undefined}
              cutSlotId={cutSlot?.id}
              onColumnClick={isAdmin && cutSlot ? handleColumnClick : undefined}
              onNotifyParticipants={isAdmin ? (slotId) => notifyParticipantsMutation.mutate(slotId) : undefined}
            />

            {/* Events legend for week view */}
            {weekData.events.length > 0 && (
              <div className="mt-6 bg-dark-900 rounded-xl border border-dark-800 p-4">
                <h3 className="text-sm font-medium text-dark-300 mb-3">
                  {t('week.events')}
                </h3>
                <div className="space-y-2">
                  {weekData.events.map((event) => {
                    const { label, badgeClass } = formatAvailability(event);
                    const isFull = event.currentParticipants >= event.maxParticipants;
                    return (
                      <div
                        key={event.id}
                        className="text-sm bg-dark-800/40 rounded-lg px-3 py-2 cursor-pointer hover:bg-dark-800/70 transition-colors"
                        onClick={() => setSelectedEvent(event)}
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-dark-100 font-medium">{event.title}</span>
                          <div className="flex items-center gap-3">
                            <span className={`px-2 py-1 rounded-full text-xs font-medium ${badgeClass}`}>
                              {label}
                            </span>
                            <span className="text-dark-400 text-xs">
                              {format(new Date(event.startDate), "dd.MM")}
                              {event.isMultiDay && (
                                <> - {format(new Date(event.endDate), "dd.MM")}</>
                              )}
                            </span>
                            {event.isUserRegistered ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-primary-500/20 text-primary-400">
                                {t('signedUp')}
                              </span>
                            ) : !event.enrollmentOpen ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-dark-700 text-dark-400">
                                {t('common:callPhone')}
                              </span>
                            ) : isFull ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-amber-500/20 text-amber-400">
                                {t('event.waitlist.join')}
                              </span>
                            ) : (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-primary-600 text-white">
                                {t('signUp')}
                              </span>
                            )}
                          </div>
                        </div>
                        {event.courseId && (
                          <Link
                            to={`/kursy#course-${event.courseId}`}
                            onClick={(e) => e.stopPropagation()}
                            className="mt-1.5 flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors"
                          >
                            <ExternalLink className="w-3 h-3" />
                            {t('event.courseDetails')}
                          </Link>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </>
        ) : null
      ) : monthLoading ? (
        <div className="flex justify-center py-12">
          <LoadingSpinner size="lg" />
        </div>
      ) : monthError ? (
        <QueryError error={monthErrorObj} onRetry={() => refetchMonth()} />
      ) : monthData ? (
        <>
          <MonthCalendar
            currentMonth={currentMonth}
            onMonthChange={setCurrentMonth}
            days={monthData.days}
            events={monthData.events}
            onDayClick={handleDayClick}
            allDaysClickable={isAdmin}
          />

          {/* Events legend */}
          {monthData.events.length > 0 && (
            <div
              ref={eventsRef}
              className="mt-6 bg-dark-900 rounded-xl border border-dark-800 p-4"
            >
              <h3 className="text-sm font-medium text-dark-300 mb-3">
                {t('eventsThisMonth')}
              </h3>

              <div className="space-y-2">
                {monthData.events.map((event) => {
                  const { label, badgeClass } = formatAvailability(event);
                  const isFull = event.currentParticipants >= event.maxParticipants;
                  const color = getEventColorByIndex(event.id, event.eventType, isFull);

                  return (
                    <div
                      key={event.id}
                      className="text-sm bg-dark-800/40 rounded-lg px-3 py-2 cursor-pointer hover:bg-dark-800/70 transition-colors"
                      onClick={() => setSelectedEvent(event)}
                    >
                      <div className="flex items-center justify-between">
                      <span className="flex items-center gap-2 text-dark-100 font-medium min-w-0">
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

                        {/* status indicator */}
                        {event.isUserRegistered ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
                            {t('signedUp')}
                          </span>
                        ) : event.eventType === 'CONTACT_DAY' ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-violet-500/20 text-violet-400">
                            {t('common:callPhone')}
                          </span>
                        ) : !event.enrollmentOpen ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-dark-700 text-dark-400">
                            {t('common:callPhone')}
                          </span>
                        ) : isFull ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-amber-500/20 text-amber-400">
                            {t('event.waitlist.join')}
                          </span>
                        ) : (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-primary-600 text-white">
                            {t('signUp')}
                          </span>
                        )}
                      </div>
                      </div>
                      {event.courseId && (
                        <Link
                          to={`/kursy#course-${event.courseId}`}
                          onClick={(e) => e.stopPropagation()}
                          className="mt-1.5 flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors"
                        >
                          <ExternalLink className="w-3 h-3" />
                          {t('event.courseDetails')}
                        </Link>
                      )}
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
        key={selectedSlotId ?? 'closed'}
        slot={slotDetail ?? null}
        isOpen={!!selectedSlotId}
        onClose={handleModalClose}
      />

      <EventSignupModal
        key={selectedEvent?.id ?? 'closed'}
        event={selectedEvent}
        isOpen={!!selectedEvent}
        onClose={() => setSelectedEvent(null)}
      />

      {isAdmin && selectedDate && (
        <CreateSlotModal
          key={selectedDate}
          isOpen={showCreateSlotModal}
          onClose={() => setShowCreateSlotModal(false)}
          defaultDate={selectedDate}
        />
      )}

      {/* Indywidualny termin */}
      <div className="mt-6 bg-dark-900 rounded-xl border border-dark-800 p-5 flex flex-col sm:flex-row items-start sm:items-center gap-4">
        <div className="flex-1">
          <p className="text-dark-100 font-semibold text-sm">{t('customSlot.title')}</p>
          <p className="text-dark-400 text-sm mt-1">{t('customSlot.description')}</p>
        </div>
        <div className="flex flex-col sm:flex-row gap-3 shrink-0">
          <a
            href="tel:+48535246673"
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-500 text-white text-sm font-medium transition-colors"
          >
            <Phone className="w-4 h-4" />
            +48 535 246 673
          </a>
          <a
            href="mailto:nextsteppro.team@gmail.com"
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-dark-800 hover:bg-dark-700 text-dark-200 text-sm font-medium transition-colors"
          >
            <Mail className="w-4 h-4" />
            {t('customSlot.email')}
          </a>
        </div>
      </div>
    </div>
  );
}
