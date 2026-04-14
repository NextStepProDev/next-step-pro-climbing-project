import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { useDateLocale } from '../utils/dateFnsLocale'
import { Calendar, Clock, MessageSquare, Users, X, Ban, ChevronDown, ChevronRight, ChevronLeft, Clock3, ListX, ExternalLink, Pencil, Check } from 'lucide-react'
import { reservationApi, calendarApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { Button } from '../components/ui/Button'
import { ConfirmModal } from '../components/ui/ConfirmModal'
import { SlotDetailModal } from '../components/calendar/SlotDetailModal'
import { EventSignupModal } from '../components/calendar/EventSignupModal'
import type { MyReservations, WaitlistEntry, EventWaitlistEntry } from '../types'

export function MyReservationsPage() {
  const { t } = useTranslation('reservations')
  const queryClient = useQueryClient()
  const [showArchive, setShowArchive] = useState(false)
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null)
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['reservations', 'upcoming'],
    queryFn: reservationApi.getMyUpcoming,
  })

  const { data: pastData, isLoading: pastLoading, isError: pastError, error: pastErrorObj } = useQuery({
    queryKey: ['reservations', 'past'],
    queryFn: reservationApi.getMyPast,
    enabled: showArchive,
  })

  const { data: slotDetail } = useQuery({
    queryKey: ['slot', selectedSlotId],
    queryFn: () => calendarApi.getSlotDetails(selectedSlotId!),
    enabled: !!selectedSlotId,
  })

  const { data: eventSummary } = useQuery({
    queryKey: ['event-summary', selectedEventId],
    queryFn: () => calendarApi.getEventSummary(selectedEventId!),
    enabled: !!selectedEventId,
  })

  const cancelMutation = useMutation({
    mutationFn: reservationApi.cancel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const cancelEventMutation = useMutation({
    mutationFn: reservationApi.cancelForEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const { data: waitlistData } = useQuery({
    queryKey: ['reservations', 'waitlist'],
    queryFn: reservationApi.getMyWaitlist,
    refetchInterval: 30_000,
  })

  const { data: eventWaitlistData } = useQuery({
    queryKey: ['reservations', 'event-waitlist'],
    queryFn: reservationApi.getMyEventWaitlist,
    refetchInterval: 30_000,
  })

  const confirmOfferMutation = useMutation({
    mutationFn: reservationApi.confirmWaitlistOffer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const leaveWaitlistMutation = useMutation({
    mutationFn: reservationApi.leaveWaitlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations', 'waitlist'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const confirmEventOfferMutation = useMutation({
    mutationFn: reservationApi.confirmEventWaitlistOffer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const leaveEventWaitlistMutation = useMutation({
    mutationFn: reservationApi.leaveEventWaitlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations', 'event-waitlist'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <QueryError error={error} onRetry={() => refetch()} />
      </div>
    )
  }

  const slots = data?.slots ?? []
  const events = data?.events ?? []
  const hasWaitlist = (waitlistData?.length ?? 0) > 0 || (eventWaitlistData?.length ?? 0) > 0
  const isEmpty = slots.length === 0 && events.length === 0 && !hasWaitlist

  const pastSlots = pastData?.slots ?? []
  const pastEvents = pastData?.events ?? []
  const hasPastData = pastSlots.length > 0 || pastEvents.length > 0

  const handleSlotModalClose = () => {
    setSelectedSlotId(null)
    queryClient.invalidateQueries({ queryKey: ['reservations'] })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
  }

  const handleEventModalClose = () => {
    setSelectedEventId(null)
    queryClient.invalidateQueries({ queryKey: ['reservations'] })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">
          {t('title')}
        </h1>
        <p className="text-dark-400">
          {t('subtitle')}
        </p>
      </div>

      {/* Sekcja PENDING_CONFIRMATION — najwyższy priorytet */}
      {waitlistData && waitlistData.some(w => w.status === 'PENDING_CONFIRMATION') && (
        <WaitlistPendingSection
          entries={waitlistData.filter(w => w.status === 'PENDING_CONFIRMATION')}
          confirmMutation={confirmOfferMutation}
          leaveMutation={leaveWaitlistMutation}
        />
      )}

      {/* Sekcja WAITING — lista oczekujących */}
      {waitlistData && waitlistData.some(w => w.status === 'WAITING') && (
        <WaitlistWaitingSection
          entries={waitlistData.filter(w => w.status === 'WAITING')}
          leaveMutation={leaveWaitlistMutation}
        />
      )}

      {/* Event waitlist — PENDING_CONFIRMATION */}
      {eventWaitlistData && eventWaitlistData.some(w => w.status === 'PENDING_CONFIRMATION') && (
        <EventWaitlistPendingSection
          entries={eventWaitlistData.filter(w => w.status === 'PENDING_CONFIRMATION')}
          confirmMutation={confirmEventOfferMutation}
          leaveMutation={leaveEventWaitlistMutation}
        />
      )}

      {/* Event waitlist — WAITING */}
      {eventWaitlistData && eventWaitlistData.some(w => w.status === 'WAITING') && (
        <EventWaitlistWaitingSection
          entries={eventWaitlistData.filter(w => w.status === 'WAITING')}
          leaveMutation={leaveEventWaitlistMutation}
        />
      )}

      {isEmpty ? (
        <div className="bg-dark-900 rounded-xl border border-dark-800 p-8 text-center">
          <Calendar className="w-12 h-12 text-dark-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-dark-300 mb-2">
            {t('empty.title')}
          </h3>
          <p className="text-dark-500 mb-4">
            {t('empty.message')}
          </p>
          <a href="/calendar">
            <Button variant="primary">{t('empty.goToCalendar')}</Button>
          </a>
        </div>
      ) : (
        <UpcomingReservations
          slots={slots}
          events={events}
          cancelMutation={cancelMutation}
          cancelEventMutation={cancelEventMutation}
          onSlotClick={setSelectedSlotId}
          onEventClick={setSelectedEventId}
        />
      )}

      {cancelMutation.isError && (
        <div className="mt-4 p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
          {getErrorMessage(cancelMutation.error)}
        </div>
      )}
      {cancelEventMutation.isError && (
        <div className="mt-4 p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
          {getErrorMessage(cancelEventMutation.error)}
        </div>
      )}

      {/* Archive */}
      <div className="mt-8">
        <button
          onClick={() => setShowArchive(!showArchive)}
          className="flex items-center gap-2 text-sm text-dark-500 hover:text-dark-300 transition-colors mb-3"
        >
          {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
          {t('archive')}
        </button>

        {showArchive && (
          <div>
            {pastLoading && (
              <div className="flex justify-center py-6">
                <LoadingSpinner size="md" />
              </div>
            )}
            {pastError && (
              <div className="p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
                {getErrorMessage(pastErrorObj)}
              </div>
            )}
            {pastData && !hasPastData && (
              <p className="text-dark-500 text-sm">{t('noPastReservations')}</p>
            )}
            {pastData && hasPastData && (
              <PastReservations slots={pastSlots} events={pastEvents} />
            )}
          </div>
        )}
      </div>

      <SlotDetailModal
        slot={slotDetail ?? null}
        isOpen={!!selectedSlotId}
        onClose={handleSlotModalClose}
      />

      <EventSignupModal
        event={eventSummary ?? null}
        isOpen={!!selectedEventId}
        onClose={handleEventModalClose}
      />
    </div>
  )
}

function UpcomingReservations({
  slots,
  events,
  cancelMutation,
  cancelEventMutation,
  onSlotClick,
  onEventClick,
}: {
  slots: MyReservations['slots']
  events: MyReservations['events']
  cancelMutation: ReturnType<typeof useMutation<void, Error, string>>
  cancelEventMutation: ReturnType<typeof useMutation<void, Error, string>>
  onSlotClick: (slotId: string) => void
  onEventClick: (eventId: string) => void
}) {
  const { t } = useTranslation('reservations')
  const { t: tc } = useTranslation('common')
  const { t: tcal } = useTranslation('calendar')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [confirmCancel, setConfirmCancel] = useState<{ type: 'slot' | 'event'; id: string } | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editCount, setEditCount] = useState(1)

  const updateParticipantsMutation = useMutation({
    mutationFn: ({ id, participants }: { id: string; participants: number }) =>
      reservationApi.updateParticipants(id, participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      setEditingId(null)
    },
  })

  const updateEventParticipantsMutation = useMutation({
    mutationFn: ({ id, participants }: { id: string; participants: number }) =>
      reservationApi.updateEventParticipants(id, participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      setEditingId(null)
    },
  })

  return (
    <div className="space-y-6">
      {events.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
            {t('events')}
          </h2>
          {events.map((event) => {
            const isCancelledByAdmin = event.cancelledByAdmin
            return (
            <div
              key={event.eventId}
              className={
                isCancelledByAdmin
                  ? 'bg-rose-500/5 rounded-xl border border-rose-500/30 p-4 sm:p-6 cursor-pointer hover:border-rose-500/50 transition-colors'
                  : 'bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-6 cursor-pointer hover:border-dark-700 transition-colors'
              }
              onClick={() => onEventClick(event.eventId)}
            >
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    {isCancelledByAdmin ? (
                      <Ban className="w-5 h-5 text-rose-400" />
                    ) : null}
                    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded ${isCancelledByAdmin ? 'bg-rose-500/20 text-rose-400' : 'bg-primary-500/20 text-primary-400'}`}>
                      {tc(`eventTypes.${event.eventType}`)}
                    </span>
                    <span className="font-medium text-dark-100">
                      {event.eventTitle}
                    </span>
                    {isCancelledByAdmin && (
                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-rose-500/20 text-rose-400">
                        {t('cancelledByAdmin')}
                      </span>
                    )}
                    {event.courseId && (
                      <Link
                        to={`/kursy#course-${event.courseId}`}
                        onClick={(e) => e.stopPropagation()}
                        className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors shrink-0"
                      >
                        <ExternalLink className="w-3 h-3" />
                        {tcal('event.courseDetails')}
                      </Link>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-dark-400">
                    <Calendar className="w-5 h-5" />
                    <span>
                      {format(new Date(event.startDate), 'd MMMM', { locale })}
                      {' - '}
                      {format(new Date(event.endDate), 'd MMMM yyyy', { locale })}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-2" onClick={(e) => e.stopPropagation()}>
                    <Users className="w-4 h-4 text-dark-400 shrink-0" />
                    {!isCancelledByAdmin && editingId === event.eventId ? (
                      <div className="flex items-center gap-2 flex-wrap">
                        <button
                          type="button"
                          onClick={() => setEditCount(Math.max(1, editCount - 1))}
                          className="w-6 h-6 rounded bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-sm font-bold flex items-center justify-center"
                        >-</button>
                        <span className="text-dark-100 font-medium w-4 text-center text-sm">{editCount}</span>
                        <button
                          type="button"
                          onClick={() => setEditCount(Math.min(event.spotsAvailable, editCount + 1))}
                          disabled={editCount >= event.spotsAvailable}
                          className="w-6 h-6 rounded bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-sm font-bold flex items-center justify-center disabled:opacity-40"
                        >+</button>
                        <button
                          type="button"
                          onClick={() => updateEventParticipantsMutation.mutate({ id: event.eventId, participants: editCount })}
                          disabled={updateEventParticipantsMutation.isPending || editCount === event.participants}
                          className="flex items-center gap-1 text-xs px-2 py-1 rounded bg-primary-500/20 text-primary-400 hover:bg-primary-500/30 transition-colors disabled:opacity-50"
                        >
                          <Check className="w-3 h-3" />
                          {t('updateParticipants.save')}
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingId(null)}
                          className="text-xs px-2 py-1 rounded bg-dark-800 text-dark-400 hover:text-dark-300 transition-colors"
                        >
                          {t('updateParticipants.cancel')}
                        </button>
                        {updateEventParticipantsMutation.isError && (
                          <span className="text-xs text-rose-400">{getErrorMessage(updateEventParticipantsMutation.error)}</span>
                        )}
                      </div>
                    ) : (
                      <div className="flex items-center gap-2">
                        <span className="text-sm text-dark-400">{t('spotsReserved', { count: event.participants })}</span>
                        {!isCancelledByAdmin && (
                          <button
                            type="button"
                            onClick={() => { setEditingId(event.eventId); setEditCount(event.participants) }}
                            className="flex items-center gap-1 text-xs text-dark-500 hover:text-dark-300 transition-colors"
                          >
                            <Pencil className="w-3 h-3" />
                            {t('updateParticipants.edit')}
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                  {event.comment && (
                    <div className="flex items-start gap-2 mt-2 text-sm text-dark-400">
                      <MessageSquare className="w-4 h-4 mt-0.5 shrink-0" />
                      <span>"{event.comment}"</span>
                    </div>
                  )}
                </div>

                {!isCancelledByAdmin && (
                  <div className="flex items-center gap-2">
                    <Button
                      variant="danger"
                      size="sm"
                      loading={cancelEventMutation.isPending}
                      onClick={(e) => {
                        e.stopPropagation()
                        setConfirmCancel({ type: 'event', id: event.eventId })
                      }}
                    >
                      <X className="w-4 h-4 mr-1" />
                      {t('cancelEvent')}
                    </Button>
                  </div>
                )}
              </div>
            </div>
            )
          })}
        </div>
      )}

      {slots.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
            {t('trainings')}
          </h2>
          {slots.map((reservation) => {
            const dateObj = new Date(reservation.date)
            const isCancelledByAdmin = reservation.status === 'CANCELLED_BY_ADMIN'
            return (
              <div
                key={reservation.id}
                className={
                  isCancelledByAdmin
                    ? 'bg-rose-500/5 rounded-xl border border-rose-500/30 p-4 sm:p-6 cursor-pointer hover:border-rose-500/50 transition-colors'
                    : 'bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-6 cursor-pointer hover:border-dark-700 transition-colors'
                }
                onClick={() => onSlotClick(reservation.timeSlotId)}
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      {isCancelledByAdmin ? (
                        <Ban className="w-5 h-5 text-rose-400" />
                      ) : (
                        <Calendar className="w-5 h-5 text-primary-400" />
                      )}
                      <span className="font-medium text-dark-100 capitalize">
                        {format(dateObj, 'EEEE, d MMMM yyyy', { locale })}
                      </span>
                      {isCancelledByAdmin && (
                        <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-rose-500/20 text-rose-400">
                          {t('cancelledByAdmin')}
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-3 text-dark-400">
                      <Clock className="w-5 h-5" />
                      <span>
                        {reservation.startTime.slice(0, 5)} -{' '}
                        {reservation.endTime.slice(0, 5)}
                      </span>
                    </div>
                    {!isCancelledByAdmin && (
                      <div className="flex items-center gap-2 mt-2" onClick={(e) => e.stopPropagation()}>
                        <Users className="w-4 h-4 text-dark-400 shrink-0" />
                        {editingId === reservation.id ? (
                          <div className="flex items-center gap-2 flex-wrap">
                            <button
                              type="button"
                              onClick={() => setEditCount(Math.max(1, editCount - 1))}
                              className="w-6 h-6 rounded bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-sm font-bold flex items-center justify-center"
                            >-</button>
                            <span className="text-dark-100 font-medium w-4 text-center text-sm">{editCount}</span>
                            <button
                              type="button"
                              onClick={() => setEditCount(Math.min(reservation.spotsAvailable, editCount + 1))}
                              disabled={editCount >= reservation.spotsAvailable}
                              className="w-6 h-6 rounded bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-sm font-bold flex items-center justify-center disabled:opacity-40"
                            >+</button>
                            <button
                              type="button"
                              onClick={() => updateParticipantsMutation.mutate({ id: reservation.id, participants: editCount })}
                              disabled={updateParticipantsMutation.isPending || editCount === reservation.participants}
                              className="flex items-center gap-1 text-xs px-2 py-1 rounded bg-primary-500/20 text-primary-400 hover:bg-primary-500/30 transition-colors disabled:opacity-50"
                            >
                              <Check className="w-3 h-3" />
                              {t('updateParticipants.save')}
                            </button>
                            <button
                              type="button"
                              onClick={() => setEditingId(null)}
                              className="text-xs px-2 py-1 rounded bg-dark-800 text-dark-400 hover:text-dark-300 transition-colors"
                            >
                              {t('updateParticipants.cancel')}
                            </button>
                            {updateParticipantsMutation.isError && (
                              <span className="text-xs text-rose-400">{getErrorMessage(updateParticipantsMutation.error)}</span>
                            )}
                          </div>
                        ) : (
                          <div className="flex items-center gap-2">
                            <span className="text-sm text-dark-400">{t('spotsReserved', { count: reservation.participants })}</span>
                            <button
                              type="button"
                              onClick={() => { setEditingId(reservation.id); setEditCount(reservation.participants) }}
                              className="flex items-center gap-1 text-xs text-dark-500 hover:text-dark-300 transition-colors"
                            >
                              <Pencil className="w-3 h-3" />
                              {t('updateParticipants.edit')}
                            </button>
                          </div>
                        )}
                      </div>
                    )}
                    {reservation.eventTitle && (
                      <div className="mt-2 inline-block px-2 py-1 bg-primary-500/10 text-primary-400 text-sm rounded">
                        {reservation.eventTitle}
                      </div>
                    )}
                    {reservation.comment && (
                      <div className="flex items-start gap-2 mt-2 text-sm text-dark-400">
                        <MessageSquare className="w-4 h-4 mt-0.5 shrink-0" />
                        <span>"{reservation.comment}"</span>
                      </div>
                    )}
                  </div>

                  {!isCancelledByAdmin && (
                    <div className="flex items-center gap-2">
                      <Button
                        variant="danger"
                        size="sm"
                        loading={cancelMutation.isPending}
                        onClick={(e) => {
                          e.stopPropagation()
                          setConfirmCancel({ type: 'slot', id: reservation.id })
                        }}
                      >
                        <X className="w-4 h-4 mr-1" />
                        {t('cancelSlot')}
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      <ConfirmModal
        isOpen={!!confirmCancel}
        onClose={() => setConfirmCancel(null)}
        onConfirm={() => {
          if (confirmCancel?.type === 'event') {
            cancelEventMutation.mutate(confirmCancel.id)
          } else if (confirmCancel) {
            cancelMutation.mutate(confirmCancel.id)
          }
        }}
        title={t('confirmCancel.title')}
        message={
          confirmCancel?.type === 'event'
            ? t('confirmCancel.eventMessage')
            : t('confirmCancel.slotMessage')
        }
        confirmText={t('confirmCancel.confirm')}
      />
    </div>
  )
}

const ARCHIVE_PAGE_SIZE = 15

function PastReservations({
  slots,
  events,
}: {
  slots: MyReservations['slots']
  events: MyReservations['events']
}) {
  const { t } = useTranslation('reservations')
  const { t: tc } = useTranslation('common')
  const locale = useDateLocale()
  const [page, setPage] = useState(1)

  const totalItems = events.length + slots.length
  const totalPages = Math.max(1, Math.ceil(totalItems / ARCHIVE_PAGE_SIZE))
  const safePage = Math.min(page, totalPages)

  const start = (safePage - 1) * ARCHIVE_PAGE_SIZE
  const end = safePage * ARCHIVE_PAGE_SIZE

  // Events come first, then slots
  const visibleEvents = events.slice(
    Math.max(0, start),
    Math.min(events.length, end),
  )
  const slotsStart = Math.max(0, start - events.length)
  const slotsEnd = Math.max(0, end - events.length)
  const visibleSlots = slots.slice(slotsStart, slotsEnd)

  return (
    <div className="space-y-6 opacity-60">
      {visibleEvents.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
            {t('events')}
          </h2>
          {visibleEvents.map((event) => (
            <div
              key={event.eventId}
              className="bg-dark-900 rounded-xl border border-dark-800/50 p-4 sm:p-6"
            >
              <div className="flex-1">
                <div className="flex items-center gap-3 mb-2">
                  <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
                    {tc(`eventTypes.${event.eventType}`)}
                  </span>
                  <span className="font-medium text-dark-100">
                    {event.eventTitle}
                  </span>
                </div>
                <div className="flex items-center gap-3 text-dark-400">
                  <Calendar className="w-5 h-5" />
                  <span>
                    {format(new Date(event.startDate), 'd MMMM', { locale })}
                    {' - '}
                    {format(new Date(event.endDate), 'd MMMM yyyy', { locale })}
                  </span>
                </div>
                {event.participants > 1 && (
                  <div className="flex items-center gap-2 mt-2 text-sm text-dark-400">
                    <Users className="w-4 h-4" />
                    <span>{t('spotsReserved', { count: event.participants })}</span>
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {visibleSlots.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
            {t('trainings')}
          </h2>
          {visibleSlots.map((reservation) => {
            const dateObj = new Date(reservation.date)
            const isCancelledByAdmin = reservation.status === 'CANCELLED_BY_ADMIN'
            const isCancelled = reservation.status === 'CANCELLED' || isCancelledByAdmin
            return (
              <div
                key={reservation.id}
                className="bg-dark-900 rounded-xl border border-dark-800/50 p-4 sm:p-6"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-3 mb-2">
                    <Calendar className="w-5 h-5 text-dark-500" />
                    <span className="font-medium text-dark-100 capitalize">
                      {format(dateObj, 'EEEE, d MMMM yyyy', { locale })}
                    </span>
                    {isCancelled && (
                      <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-dark-700 text-dark-400">
                        {t('cancelled')}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-dark-400">
                    <Clock className="w-5 h-5" />
                    <span>
                      {reservation.startTime.slice(0, 5)} -{' '}
                      {reservation.endTime.slice(0, 5)}
                    </span>
                  </div>
                  {reservation.participants > 1 && (
                    <div className="flex items-center gap-2 mt-2 text-sm text-dark-400">
                      <Users className="w-4 h-4" />
                      <span>{t('spotsReserved', { count: reservation.participants })}</span>
                    </div>
                  )}
                  {reservation.eventTitle && (
                    <div className="mt-2 inline-block px-2 py-1 bg-primary-500/10 text-primary-400 text-sm rounded">
                      {reservation.eventTitle}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between pt-2">
          <span className="text-sm text-dark-500">
            {t('totalItems', { count: totalItems })}
          </span>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={safePage <= 1}
              className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="text-sm text-dark-300 min-w-[80px] text-center">
              {safePage} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={safePage >= totalPages}
              className="p-2 text-dark-400 hover:text-dark-100 hover:bg-dark-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function WaitlistPendingSection({
  entries,
  confirmMutation,
  leaveMutation,
}: {
  entries: WaitlistEntry[]
  confirmMutation: ReturnType<typeof useMutation<{ reservationId: string; success: boolean; message: string }, Error, string>>
  leaveMutation: ReturnType<typeof useMutation<void, Error, string>>
}) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()

  return (
    <div className="mb-6 space-y-3">
      <h2 className="text-sm font-semibold text-amber-400 uppercase tracking-wider flex items-center gap-2">
        <Clock3 className="w-4 h-4" />
        {t('waitlist.pending.title')}
      </h2>
      {entries.map((entry) => (
        <div
          key={entry.id}
          className="bg-amber-500/5 rounded-xl border-2 border-amber-500/30 p-4 sm:p-5"
        >
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <Clock3 className="w-4 h-4 text-amber-400" />
                <span className="font-medium text-dark-100 capitalize">
                  {format(new Date(entry.slotDate), 'EEEE, d MMMM yyyy', { locale })}
                </span>
              </div>
              <div className="flex items-center gap-2 text-dark-400 text-sm">
                <Clock className="w-4 h-4" />
                <span>{entry.slotStartTime.slice(0, 5)} - {entry.slotEndTime.slice(0, 5)}</span>
                {entry.slotTitle && <span className="text-primary-400">· {entry.slotTitle}</span>}
              </div>
              {entry.confirmationDeadline && (
                <p className="mt-2 text-amber-300/80 text-sm">
                  {t('waitlist.pending.deadline')}{' '}
                  <span className="font-semibold">
                    {format(new Date(entry.confirmationDeadline), 'dd.MM.yyyy HH:mm')}
                  </span>
                </p>
              )}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="primary"
                size="sm"
                loading={confirmMutation.isPending}
                onClick={() => confirmMutation.mutate(entry.id)}
              >
                {t('waitlist.pending.confirm')}
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={leaveMutation.isPending}
                onClick={() => leaveMutation.mutate(entry.slotId)}
              >
                {t('waitlist.pending.decline')}
              </Button>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

function WaitlistWaitingSection({
  entries,
  leaveMutation,
}: {
  entries: WaitlistEntry[]
  leaveMutation: ReturnType<typeof useMutation<void, Error, string>>
}) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()

  return (
    <div className="mb-6 space-y-3">
      <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider flex items-center gap-2">
        <ListX className="w-4 h-4" />
        {t('waitlist.waiting.title')}
      </h2>
      {entries.map((entry) => (
        <div
          key={entry.id}
          className="bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-5"
        >
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <Calendar className="w-4 h-4 text-dark-400" />
                <span className="font-medium text-dark-100 capitalize">
                  {format(new Date(entry.slotDate), 'EEEE, d MMMM yyyy', { locale })}
                </span>
              </div>
              <div className="flex items-center gap-2 text-dark-400 text-sm">
                <Clock className="w-4 h-4" />
                <span>{entry.slotStartTime.slice(0, 5)} - {entry.slotEndTime.slice(0, 5)}</span>
                {entry.slotTitle && <span className="text-primary-400">· {entry.slotTitle}</span>}
              </div>
            </div>
            <Button
              variant="secondary"
              size="sm"
              loading={leaveMutation.isPending}
              onClick={() => leaveMutation.mutate(entry.slotId)}
            >
              {t('waitlist.waiting.leave')}
            </Button>
          </div>
        </div>
      ))}
    </div>
  )
}

function EventWaitlistPendingSection({
  entries,
  confirmMutation,
  leaveMutation,
}: {
  entries: EventWaitlistEntry[]
  confirmMutation: ReturnType<typeof useMutation<{ eventId: string; success: boolean; message: string; slotsReserved: number }, Error, string>>
  leaveMutation: ReturnType<typeof useMutation<void, Error, string>>
}) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()

  return (
    <div className="mb-6 space-y-3">
      <h2 className="text-sm font-semibold text-amber-400 uppercase tracking-wider flex items-center gap-2">
        <Clock3 className="w-4 h-4" />
        {t('waitlist.pending.title')}
      </h2>
      {entries.map((entry) => (
        <div
          key={entry.id}
          className="bg-amber-500/5 rounded-xl border-2 border-amber-500/30 p-4 sm:p-5"
        >
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <Clock3 className="w-4 h-4 text-amber-400" />
                <span className="font-medium text-dark-100">{entry.eventTitle}</span>
              </div>
              <div className="flex items-center gap-2 text-dark-400 text-sm">
                <Calendar className="w-4 h-4" />
                <span>
                  {format(new Date(entry.eventStartDate), 'd MMMM', { locale })}
                  {entry.eventStartDate !== entry.eventEndDate && (
                    <> – {format(new Date(entry.eventEndDate), 'd MMMM yyyy', { locale })}</>
                  )}
                  {entry.eventStartDate === entry.eventEndDate && (
                    <> {format(new Date(entry.eventStartDate), 'yyyy', { locale })}</>
                  )}
                </span>
              </div>
              {entry.confirmationDeadline && (
                <p className="mt-2 text-amber-300/80 text-sm">
                  {t('waitlist.pending.deadline')}{' '}
                  <span className="font-semibold">
                    {format(new Date(entry.confirmationDeadline), 'dd.MM.yyyy HH:mm')}
                  </span>
                </p>
              )}
            </div>
            <div className="flex items-center gap-2">
              <Button
                variant="primary"
                size="sm"
                loading={confirmMutation.isPending}
                onClick={() => confirmMutation.mutate(entry.id)}
              >
                {t('waitlist.pending.confirm')}
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={leaveMutation.isPending}
                onClick={() => leaveMutation.mutate(entry.eventId)}
              >
                {t('waitlist.pending.decline')}
              </Button>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}

function EventWaitlistWaitingSection({
  entries,
  leaveMutation,
}: {
  entries: EventWaitlistEntry[]
  leaveMutation: ReturnType<typeof useMutation<void, Error, string>>
}) {
  const { t } = useTranslation('reservations')
  const locale = useDateLocale()

  return (
    <div className="mb-6 space-y-3">
      <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider flex items-center gap-2">
        <ListX className="w-4 h-4" />
        {t('waitlist.waiting.title')}
      </h2>
      {entries.map((entry) => (
        <div
          key={entry.id}
          className="bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-5"
        >
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex-1">
              <div className="flex items-center gap-2 mb-1">
                <Calendar className="w-4 h-4 text-dark-400" />
                <span className="font-medium text-dark-100">{entry.eventTitle}</span>
              </div>
              <div className="flex items-center gap-2 text-dark-400 text-sm">
                <span>
                  {format(new Date(entry.eventStartDate), 'd MMMM', { locale })}
                  {entry.eventStartDate !== entry.eventEndDate && (
                    <> – {format(new Date(entry.eventEndDate), 'd MMMM yyyy', { locale })}</>
                  )}
                  {entry.eventStartDate === entry.eventEndDate && (
                    <> {format(new Date(entry.eventStartDate), 'yyyy', { locale })}</>
                  )}
                </span>
              </div>
            </div>
            <Button
              variant="secondary"
              size="sm"
              loading={leaveMutation.isPending}
              onClick={() => leaveMutation.mutate(entry.eventId)}
            >
              {t('waitlist.waiting.leave')}
            </Button>
          </div>
        </div>
      ))}
    </div>
  )
}
