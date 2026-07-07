import { useState, useCallback, useRef, useEffect, useMemo } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useSearchParams, useLocation, Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { PageHead } from "../components/ui/PageHead";
import { format, startOfWeek, addWeeks, subWeeks } from "date-fns";
import { calendarApi, reservationApi, adminApi } from "../api/client";
import { getAccessToken } from "../utils/tokenStorage";
import { useAuth } from "../context/AuthContext";
import { MonthCalendar } from "../components/calendar/MonthCalendar";
import { WeekCalendar } from "../components/calendar/WeekCalendar";
import { DayView } from "../components/calendar/DayView";
import { SlotDetailModal } from "../components/calendar/SlotDetailModal";
import { EventSignupModal } from "../components/calendar/EventSignupModal";
import { CreateSlotModal } from "../components/calendar/CreateSlotModal";
import { ProposeTrainingModal, type ProposeWindow } from "../components/calendar/ProposeTrainingModal";
import { LoadingSpinner } from "../components/ui/LoadingSpinner";
import { QueryError } from "../components/ui/QueryError";
import { Phone, Mail, ExternalLink, Scissors, Copy, X, Bell, CalendarPlus } from "lucide-react";
import { formatAvailability, buildEventColorMap } from "../utils/events";
import { useCalendarPromo } from "../hooks/useCalendarPromo";
import type { EventSummary, TimeSlot } from "../types";

export function CalendarPage() {
  const { t } = useTranslation('calendar');
  const promo = useCalendarPromo();
  const { isAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  // Back-link target so CourseDetailPage returns to the calendar instead of the course list.
  const courseReturnTo = location.pathname + location.search;
  const [viewMode, setViewMode] = useState<'month' | 'week'>(() => {
    return searchParams.get("view") === 'month' ? 'month' : 'week';
  });
  const [currentMonth, setCurrentMonth] = useState(() => {
    const dateParam = searchParams.get("date");
    return dateParam ? new Date(dateParam) : new Date();
  });
  const [currentWeekStart, setCurrentWeekStart] = useState(() => {
    const dateParam = searchParams.get("date");
    const base = dateParam ? new Date(dateParam) : new Date();
    return startOfWeek(base, { weekStartsOn: 1 });
  });
  const [selectedDate, setSelectedDate] = useState<string | null>(
    searchParams.get("view") === 'month' ? searchParams.get("date") : null,
  );
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(
    searchParams.get("slot")
  );
  const [selectedEvent, setSelectedEvent] = useState<EventSummary | null>(null);
  const [showCreateSlotModal, setShowCreateSlotModal] = useState(false);
  // Propozycja terminu: data startowa + opcjonalne okno dostępności (ogranicza godziny)
  const [proposeContext, setProposeContext] = useState<{ date: string; window?: ProposeWindow } | null>(null);
  const [cutSlot, setCutSlot] = useState<{ id: string; date: string; startTime: string; endTime: string } | null>(null);
  const [copiedSlot, setCopiedSlot] = useState<{ id: string; date: string; startTime: string; endTime: string; title?: string; maxParticipants: number; isAvailabilityWindow: boolean } | null>(null);
  const [notifyToast, setNotifyToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const lastSlotMoveRef = useRef<Map<string, { previousDate: string; previousStartTime: string; previousEndTime: string }>>(new Map());
  const [pendingSlotMove, setPendingSlotMove] = useState<{
    slotId: string
    originalDate: string
    originalStartTime: string
    originalEndTime: string
  } | null>(null);
  const pendingSlotMoveRef = useRef(pendingSlotMove);
  useEffect(() => { pendingSlotMoveRef.current = pendingSlotMove; }, [pendingSlotMove]);
  const eventsRef = useRef<HTMLDivElement>(null);

  const yearMonth = format(currentMonth, "yyyy-MM");

  const {
    data: monthData,
    isLoading: monthLoading,
    isFetching: monthFetching,
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
    isFetching: weekFetching,
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
    // Detail-by-id feeding the slot modal: opening a different slot must not
    // flash the previously opened slot. Opt out of the global keepPreviousData.
    placeholderData: undefined,
  });

  // Deep link from a shared event link (/calendar?event=<id>) — opens the
  // signup modal for that event directly (e.g. landing from EventPage CTA).
  const eventParam = searchParams.get("event");
  const { data: deepLinkEvent } = useQuery({
    queryKey: ["event", eventParam],
    queryFn: () => calendarApi.getEventSummary(eventParam!),
    enabled: !!eventParam,
    // Detail-by-id feeding the signup modal: opt out so a changed deep link
    // never flashes the previous event.
    placeholderData: undefined,
  });
  const openedEventParamRef = useRef<string | null>(null);
  useEffect(() => {
    if (!eventParam) {
      openedEventParamRef.current = null;
      return;
    }
    if (deepLinkEvent && openedEventParamRef.current !== eventParam) {
      openedEventParamRef.current = eventParam;
      setSelectedEvent(deepLinkEvent);
    }
  }, [eventParam, deepLinkEvent]);

  const monthColorMap = useMemo(
    () => monthData ? buildEventColorMap(monthData.events) : new Map(),
    [monthData],
  );

  const weekColorMap = useMemo(
    () => weekData ? buildEventColorMap(weekData.events) : new Map(),
    [weekData],
  );

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
      } else if (date >= format(new Date(), "yyyy-MM-dd")) {
        // Pusty przyszły dzień: otwórz widok dnia z CTA "Zaproponuj termin"
        setSelectedDate(date);
        setSearchParams({ date });
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
    if (mode === 'month') {
      setSearchParams({ view: 'month' });
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
    setSearchParams(prev => {
      if (!prev.has('slot')) return prev;
      const next = new URLSearchParams(prev);
      next.delete('slot');
      return next;
    });
  }, [setSearchParams]);

  const handleEventModalClose = useCallback(() => {
    setSelectedEvent(null);
    setSearchParams(prev => {
      if (!prev.has('event')) return prev;
      const next = new URLSearchParams(prev);
      next.delete('event');
      return next;
    });
  }, [setSearchParams]);

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
    setPendingSlotMove(prev => {
      if (prev && prev.slotId !== slotId) {
        // Auto-zatwierdź poprzedni pending slot przy dragowaniu innego
        lastSlotMoveRef.current.set(prev.slotId, {
          previousDate: prev.originalDate,
          previousStartTime: prev.originalStartTime,
          previousEndTime: prev.originalEndTime,
        });
      }
      if (prev?.slotId === slotId) return prev; // re-drag tego samego — zachowaj oryginał
      return { slotId, originalDate: oldDate, originalStartTime: oldStartTime, originalEndTime: oldEndTime };
    });
    moveSlotMutation.mutate({ slotId, date: newDate, startTime: newStartTime, endTime: newEndTime });
  }, [moveSlotMutation]);

  const handleConfirmSlotMove = useCallback((slotId: string) => {
    setPendingSlotMove(prev => {
      if (!prev || prev.slotId !== slotId) return prev;
      lastSlotMoveRef.current.set(slotId, {
        previousDate: prev.originalDate,
        previousStartTime: prev.originalStartTime,
        previousEndTime: prev.originalEndTime,
      });
      return null;
    });
    notifyParticipantsMutation.mutate(slotId);
  }, [notifyParticipantsMutation]);

  const handleCancelSlotMove = useCallback((slotId: string) => {
    setPendingSlotMove(prev => {
      if (!prev || prev.slotId !== slotId) return prev;
      moveSlotMutation.mutate({
        slotId,
        date: prev.originalDate,
        startTime: prev.originalStartTime,
        endTime: prev.originalEndTime,
      });
      return null;
    });
  }, [moveSlotMutation]);

  const handleNotifyParticipants = useCallback((slotId: string) => {
    notifyParticipantsMutation.mutate(slotId);
  }, [notifyParticipantsMutation]);

  // Helper: fire raw fetch revert with keepalive (works during page unload)
  const fireRevertFetch = useCallback((pending: NonNullable<typeof pendingSlotMove>) => {
    const token = getAccessToken();
    void fetch(`/api/admin/slots/${pending.slotId}`, {
      method: 'PUT',
      keepalive: true,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        date: pending.originalDate,
        startTime: pending.originalStartTime,
        endTime: pending.originalEndTime,
        sendNotifications: false,
      }),
    });
  }, []);

  // Auto-revert on SPA navigation away (component unmount)
  useEffect(() => {
    return () => {
      const pending = pendingSlotMoveRef.current;
      if (pending) fireRevertFetch(pending);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Auto-revert on page refresh / tab close
  useEffect(() => {
    if (!pendingSlotMove) return;
    const handleBeforeUnload = () => {
      const pending = pendingSlotMoveRef.current;
      if (pending) fireRevertFetch(pending);
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => { window.removeEventListener('beforeunload', handleBeforeUnload); };
  }, [pendingSlotMove, fireRevertFetch]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setCutSlot(null);
        setCopiedSlot(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleSlotCut = useCallback((slot: TimeSlot, date: string) => {
    setCopiedSlot(null);
    setCutSlot({ id: slot.id, date, startTime: slot.startTime, endTime: slot.endTime });
  }, []);

  const handleSlotCopy = useCallback((slot: TimeSlot, date: string) => {
    setCutSlot(null);
    setCopiedSlot({
      id: slot.id, date, startTime: slot.startTime, endTime: slot.endTime,
      title: slot.eventTitle ?? undefined,
      maxParticipants: slot.maxParticipants,
      isAvailabilityWindow: slot.isAvailabilityWindow,
    });
  }, []);

  const copySlotMutation = useMutation({
    mutationFn: adminApi.createTimeSlot,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['calendar'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'slots'] });
    },
  });

  const handleColumnClick = useCallback((date: string, startTime: string) => {
    const source = cutSlot ?? copiedSlot;
    if (!source) return;
    const [sh, sm] = source.startTime.split(':').map(Number);
    const [eh, em] = source.endTime.split(':').map(Number);
    const durationMin = (eh * 60 + em) - (sh * 60 + sm);
    const [clickH, clickM] = startTime.split(':').map(Number);
    const endAbsMin = clickH * 60 + clickM + durationMin;
    const endH = Math.floor(endAbsMin / 60);
    const endM = endAbsMin % 60;
    const endTime = `${String(endH).padStart(2, '0')}:${String(endM).padStart(2, '0')}`;

    if (cutSlot) {
      setPendingSlotMove(prev => {
        if (prev && prev.slotId !== cutSlot.id) {
          lastSlotMoveRef.current.set(prev.slotId, {
            previousDate: prev.originalDate,
            previousStartTime: prev.originalStartTime,
            previousEndTime: prev.originalEndTime,
          });
        }
        return { slotId: cutSlot.id, originalDate: cutSlot.date, originalStartTime: cutSlot.startTime, originalEndTime: cutSlot.endTime };
      });
      moveSlotMutation.mutate({ slotId: cutSlot.id, date, startTime, endTime });
      setCutSlot(null);
    } else if (copiedSlot) {
      copySlotMutation.mutate({
        date, startTime, endTime,
        maxParticipants: copiedSlot.maxParticipants,
        title: copiedSlot.title,
        isAvailabilityWindow: copiedSlot.isAvailabilityWindow,
      });
      setCopiedSlot(null);
    }
  }, [cutSlot, copiedSlot, moveSlotMutation, copySlotMutation]);

  const cancelEventMutation = useMutation({
    mutationFn: (eventId: string) => reservationApi.cancelForEvent(eventId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['calendar', 'day', selectedDate] });
      void queryClient.invalidateQueries({ queryKey: ['reservations'] });
    },
  });

  return (
    <div className={`mx-auto px-4 sm:px-6 lg:px-8 py-8 ${viewMode === 'week' && !selectedDate ? 'max-w-6xl' : 'max-w-4xl'}`}>
      <PageHead title={t('title')} description={t('metaDescription')} />
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-surface-100 mb-2">{t('title')}</h1>
        <p className="text-surface-400">
          {t('subtitle')}
        </p>

        {/* PROMOCJA — edytowalna w panelu admina (Ustawienia strony → Promocja kalendarza) */}
        {promo.enabled && promo.title && (
          <div className="calendar-promo relative mt-4 inline-block overflow-hidden bg-amber-500/10 border border-amber-500/30 rounded-xl px-5 py-4 shadow-lg [text-shadow:_0_1px_3px_rgb(0_0_0_/_45%)]">
            {promo.badge && (
              <span className="calendar-promo-badge inline-flex items-center gap-1.5 mb-2 px-2.5 py-0.5 bg-amber-400/20 border border-amber-400/40 rounded-full text-amber-300 text-[11px] font-bold uppercase tracking-wide">
                {promo.badge}
              </span>
            )}
            <p className="calendar-promo-title text-amber-400 font-bold text-base sm:text-lg">
              {promo.title}
            </p>
            <p className="calendar-promo-desc text-amber-300/85 text-xs sm:text-sm mt-1">
              {promo.description}
            </p>
            {promo.ctaLabel && promo.ctaUrl && (
              <div className="calendar-promo-cta mt-3">
                {/^https?:\/\//.test(promo.ctaUrl) ? (
                  <a
                    href={promo.ctaUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-amber-500 hover:bg-amber-400 text-surface-950 font-semibold text-sm rounded-lg transition-colors [text-shadow:none]"
                  >
                    {promo.ctaLabel}
                    <ExternalLink className="w-4 h-4" />
                  </a>
                ) : (
                  <Link
                    to={promo.ctaUrl}
                    className="inline-flex items-center gap-1.5 px-3.5 py-1.5 bg-amber-500 hover:bg-amber-400 text-surface-950 font-semibold text-sm rounded-lg transition-colors [text-shadow:none]"
                  >
                    {promo.ctaLabel}
                    <span aria-hidden>→</span>
                  </Link>
                )}
              </div>
            )}
          </div>
        )}

        {/* View mode toggle */}
        {!selectedDate && (
          <div className="mt-4 flex gap-1 bg-surface-800 rounded-lg p-1 w-fit">
            <button
              onClick={() => handleViewModeChange('month')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                viewMode === 'month'
                  ? 'bg-primary-600 text-white'
                  : 'text-surface-400 hover:text-surface-200'
              }`}
            >
              {t('viewMode.month')}
            </button>
            <button
              onClick={() => handleViewModeChange('week')}
              className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                viewMode === 'week'
                  ? 'bg-primary-600 text-white'
                  : 'text-surface-400 hover:text-surface-200'
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
          onProposeTraining={!isAdmin && selectedDate >= format(new Date(), "yyyy-MM-dd")
            ? () => setProposeContext({ date: selectedDate })
            : undefined}
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
            {/* Notify toast — fixed so it's visible even when scrolled down */}
            {notifyToast && (
              <div className={`fixed bottom-6 right-6 z-50 flex items-center gap-3 px-4 py-2.5 rounded-lg border shadow-lg max-w-sm ${
                notifyToast.type === 'success'
                  ? 'bg-surface-900 border-primary-500/30 text-primary-300'
                  : 'bg-surface-900 border-red-500/30 text-red-300'
              }`}>
                <Bell className="w-4 h-4 shrink-0" />
                <span className="text-sm flex-1">{notifyToast.message}</span>
                <button onClick={() => setNotifyToast(null)} className="p-1 opacity-60 hover:opacity-100 transition-opacity">
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            {/* Pending slot move banner */}
            {isAdmin && pendingSlotMove && (
              <div className="mb-3 flex items-center gap-3 px-4 py-2.5 bg-red-500/10 border border-red-500/30 rounded-lg">
                <span className="text-sm text-red-300 flex-1">
                  Slot <strong>{pendingSlotMove.originalStartTime.slice(0, 5)}–{pendingSlotMove.originalEndTime.slice(0, 5)}</strong> przeniesiony — zatwierdź lub anuluj zmiany
                </span>
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

            {/* Copy-mode banner */}
            {isAdmin && copiedSlot && (
              <div className="mb-3 flex items-center gap-3 px-4 py-2.5 bg-primary-500/10 border border-primary-500/30 rounded-lg">
                <Copy className="w-4 h-4 text-primary-400 shrink-0" />
                <span className="text-sm text-primary-300 flex-1">
                  Slot <strong>{copiedSlot.startTime.slice(0, 5)}–{copiedSlot.endTime.slice(0, 5)}</strong> skopiowany — kliknij w wolne miejsce w kalendarzu, aby wkleić kopię
                </span>
                <button
                  onClick={() => setCopiedSlot(null)}
                  className="p-1 text-primary-400 hover:text-primary-200 transition-colors"
                  title="Anuluj"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}

            <div className={`transition-opacity duration-150 ${weekFetching ? 'opacity-60' : ''}`}>
            <WeekCalendar
              startDate={weekData.startDate}
              days={weekData.days}
              events={weekData.events}
              eventColorMap={weekColorMap}
              onPrevWeek={handlePrevWeek}
              onNextWeek={handleNextWeek}
              onToday={handleTodayWeek}
              onSlotClick={handleSlotClick}
              onEventClick={setSelectedEvent}
              onDayClick={handleWeekDayClick}
              isAdmin={isAdmin}
              onSlotDrop={isAdmin ? handleSlotDrop : undefined}
              onSlotCut={isAdmin ? handleSlotCut : undefined}
              onSlotCopy={isAdmin ? handleSlotCopy : undefined}
              cutSlotId={cutSlot?.id}
              copiedSlotId={copiedSlot?.id}
              onColumnClick={isAdmin && (cutSlot || copiedSlot) ? handleColumnClick : undefined}
              onNotifyParticipants={isAdmin ? handleNotifyParticipants : undefined}
              pendingSlotId={pendingSlotMove?.slotId}
              onConfirmSlotMove={isAdmin ? handleConfirmSlotMove : undefined}
              onCancelSlotMove={isAdmin ? handleCancelSlotMove : undefined}
            />
            </div>

            {/* Events legend for week view */}
            {weekData.events.length > 0 && (
              <div className="mt-6 bg-surface-900 rounded-xl border border-surface-800 p-4">
                <h3 className="text-sm font-medium text-surface-300 mb-3">
                  {t('week.events')}
                </h3>
                <div className="space-y-2">
                  {weekData.events.map((event) => {
                    const { label, badgeClass } = formatAvailability(event);
                    // Pełny także gdy resztę miejsc trzymają zaproszenia innych — wtedy lista rezerwowa.
                    const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0));
                    const isFull = event.currentParticipants + reservedForOthers >= event.maxParticipants;
                    const color = weekColorMap.get(event.id)!;
                    return (
                      <div
                        key={event.id}
                        className="text-sm bg-surface-800/40 rounded-lg px-3 py-2 cursor-pointer hover:bg-surface-800/70 transition-colors"
                        onClick={() => setSelectedEvent(event)}
                      >
                        <div className="flex items-center justify-between">
                          <span className="flex items-center gap-2 text-surface-100 font-medium min-w-0">
                            <span className={`w-2.5 h-2.5 rounded-full shrink-0 ${color.dot}`} />
                            {event.title}
                          </span>
                          <div className="flex items-center gap-3">
                            <span className={`px-2 py-1 rounded-full text-xs font-medium ${badgeClass}`}>
                              {label}
                            </span>
                            <span className="text-surface-400 text-xs">
                              {format(new Date(event.startDate), "dd.MM")}
                              {event.isMultiDay && (
                                <> - {format(new Date(event.endDate), "dd.MM")}</>
                              )}
                            </span>
                            {event.eventType === 'UNAVAILABLE' ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-slate-500/20 text-slate-300">
                                {t('event.unavailable')}
                              </span>
                            ) : event.isUserRegistered ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-primary-500/20 text-primary-400">
                                {t('signedUp')}
                              </span>
                            ) : !event.enrollmentOpen ? (
                              <span className="px-3 py-1 text-xs font-medium rounded-full bg-surface-700 text-surface-400">
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
                            to={`/kursy/${event.courseId}`}
                            state={{ returnTo: courseReturnTo }}
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
          <div className={`transition-opacity duration-150 ${monthFetching ? 'opacity-60' : ''}`}>
            <MonthCalendar
              currentMonth={currentMonth}
              onMonthChange={setCurrentMonth}
              days={monthData.days}
              events={monthData.events}
              onDayClick={handleDayClick}
              allDaysClickable={isAdmin}
              eventColorMap={monthColorMap}
            />
          </div>

          {/* Events legend */}
          {monthData.events.length > 0 && (
            <div
              ref={eventsRef}
              className="mt-6 bg-surface-900 rounded-xl border border-surface-800 p-4"
            >
              <h3 className="text-sm font-medium text-surface-300 mb-3">
                {t('eventsThisMonth')}
              </h3>

              <div className="space-y-2">
                {monthData.events.map((event) => {
                  const { label, badgeClass } = formatAvailability(event);
                  // Pełny także gdy resztę miejsc trzymają zaproszenia innych — wtedy lista rezerwowa.
                  const reservedForOthers = Math.max(0, (event.reservedSeats ?? 0) - (event.isReservedForUser ? 1 : 0));
                  const isFull = event.currentParticipants + reservedForOthers >= event.maxParticipants;
                  const color = monthColorMap.get(event.id)!;

                  return (
                    <div
                      key={event.id}
                      className="text-sm bg-surface-800/40 rounded-lg px-3 py-2 cursor-pointer hover:bg-surface-800/70 transition-colors"
                      onClick={() => setSelectedEvent(event)}
                    >
                      <div className="flex items-center justify-between">
                      <span className="flex items-center gap-2 text-surface-100 font-medium min-w-0">
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
                        <span className="text-surface-400 text-xs">
                          {format(new Date(event.startDate), "dd.MM")}
                          {event.isMultiDay && (
                            <> - {format(new Date(event.endDate), "dd.MM")}</>
                          )}
                        </span>

                        {/* status indicator */}
                        {event.eventType === 'UNAVAILABLE' ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-slate-500/20 text-slate-300">
                            {t('event.unavailable')}
                          </span>
                        ) : event.isUserRegistered ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
                            {t('signedUp')}
                          </span>
                        ) : event.eventType === 'CONTACT_DAY' ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-indigo-500/20 text-indigo-400">
                            {t('common:callPhone')}
                          </span>
                        ) : !event.enrollmentOpen ? (
                          <span className="px-3 py-1 text-xs font-medium rounded bg-surface-700 text-surface-400">
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
                          to={`/kursy/${event.courseId}`}
                          state={{ returnTo: courseReturnTo }}
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
        key={selectedSlotId ?? 'slot-closed'}
        slot={slotDetail ?? null}
        isOpen={!!selectedSlotId}
        onClose={handleModalClose}
        onProposeInWindow={(w) => {
          handleModalClose();
          setProposeContext({ date: w.date, window: w });
        }}
      />

      <EventSignupModal
        key={selectedEvent?.id ?? 'event-closed'}
        event={selectedEvent}
        isOpen={!!selectedEvent}
        onClose={handleEventModalClose}
      />

      {isAdmin && selectedDate && (
        <CreateSlotModal
          key={selectedDate}
          isOpen={showCreateSlotModal}
          onClose={() => setShowCreateSlotModal(false)}
          defaultDate={selectedDate}
        />
      )}

      {proposeContext && (
        <ProposeTrainingModal
          key={`${proposeContext.date}-${proposeContext.window?.slotId ?? 'free'}`}
          isOpen={!!proposeContext}
          onClose={() => setProposeContext(null)}
          defaultDate={proposeContext.date}
          window={proposeContext.window}
        />
      )}

      {/* Indywidualny termin */}
      <div className="mt-6 bg-surface-900 rounded-xl border border-surface-800 p-5 flex flex-col sm:flex-row items-start sm:items-center gap-4">
        <div className="flex-1">
          <p className="text-surface-100 font-semibold text-sm">{t('customSlot.title')}</p>
          <p className="text-surface-400 text-sm mt-1">{t('customSlot.description')}</p>
        </div>
        <div className="flex flex-col sm:flex-row gap-3 shrink-0">
          {!isAdmin && (
            <button
              onClick={() => setProposeContext({ date: selectedDate ?? format(new Date(), 'yyyy-MM-dd') })}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-500 text-white text-sm font-medium transition-colors"
            >
              <CalendarPlus className="w-4 h-4" />
              {t('customSlot.propose')}
            </button>
          )}
          <a
            href="tel:+48535246673"
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary-600 hover:bg-primary-500 text-white text-sm font-medium transition-colors"
          >
            <Phone className="w-4 h-4" />
            +48 535 246 673
          </a>
          <a
            href="mailto:nextsteppro.team@gmail.com"
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-surface-800 hover:bg-surface-700 text-surface-200 text-sm font-medium transition-colors"
          >
            <Mail className="w-4 h-4" />
            {t('customSlot.email')}
          </a>
        </div>
      </div>
    </div>
  );
}
